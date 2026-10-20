package com.pulse.server;

import com.pulse.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One connected client. The thread blocks on {@code readUTF()} and reacts to
 * whatever frame arrives. Writing is synchronized so two threads cannot
 * interleave bytes in the middle of a frame.
 */
class ClientHandler implements Runnable {

    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 16;
    private static final int MAX_MESSAGE = 2000;

    private final ChatServer server;
    private final Socket socket;
    private final int id;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private String key;

    ClientHandler(ChatServer server, Socket socket, int id) {
        this.server = server;
        this.socket = socket;
        this.id = id;
    }

    int getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            if (!join()) {
                return;
            }

            readFrames();
        } catch (EOFException | SocketException e) {
            // The other side closed the connection. Not an error.
        } catch (IOException e) {
            if (!closed.get()) {
                server.log("Connection " + id + " failed: " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Handles the first frame, which must be JOIN. Returns false when the
     * connection should be dropped straight away.
     */
    private boolean join() throws IOException {
        String[] parts = Protocol.split(in.readUTF());
        if (parts.length < 2 || !Protocol.JOIN.equals(parts[0])) {
            send(Protocol.frame(Protocol.ERROR, "Expected JOIN as the first message"));
            return false;
        }

        String name = parts[1].trim();
        String problem = checkName(name);
        if (problem != null) {
            send(Protocol.frame(Protocol.ERROR, problem));
            return false;
        }

        key = name.toLowerCase();
        if (!server.register(key, this)) {
            send(Protocol.frame(Protocol.ERROR, "That name is already taken"));
            return false;
        }

        username = name;
        send(Protocol.frame(Protocol.WELCOME, username));
        server.log(username + " joined");

        server.broadcastExcept(this, Protocol.frame(Protocol.SYSTEM, username + " joined the chat"));
        server.clientsChanged();
        return true;
    }

    private void readFrames() throws IOException {
        while (!closed.get()) {
            String[] parts = Protocol.split(in.readUTF());
            switch (parts[0]) {
                case Protocol.MSG -> handleMessage(parts);
                case Protocol.USERS -> send(Protocol.frame(Protocol.USERLIST,
                        server.getUsernames().toArray(new String[0])));
                case Protocol.QUIT -> {
                    return;
                }
                default -> server.log("Ignored unknown frame from " + username);
            }
        }
    }

    private void handleMessage(String[] parts) {
        if (parts.length < 2) {
            return;
        }

        String text = parts[1].strip();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_MESSAGE) {
            text = text.substring(0, MAX_MESSAGE);
        }

        server.broadcast(Protocol.frame(Protocol.CHAT, username, text));
        server.countMessage();
        server.log(username + ": " + shorten(text));
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 77) + "...";
    }

    /**
     * Rules are simple on purpose: letters, digits and underscore, so a name
     * can never break the comma separated user list or a future command.
     */
    static String checkName(String name) {
        if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
            return "Name must be " + MIN_NAME + " to " + MAX_NAME + " characters";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_';
            if (!ok) {
                return "Use letters, digits and underscore only";
            }
        }
        return null;
    }

    /**
     * Writes one frame. Called from the broadcast thread and from this
     * client's own thread, hence the synchronized block.
     */
    synchronized void send(String frame) {
        if (closed.get() || out == null) {
            return;
        }
        try {
            out.writeUTF(frame);
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing useful to do here.
        }
    }

    private void cleanup() {
        close();

        if (key != null) {
            server.unregister(key);
            if (username != null) {
                server.log(username + " left");
                server.broadcast(Protocol.frame(Protocol.SYSTEM, username + " left the chat"));
            }
            server.clientsChanged();
        }
    }
}
