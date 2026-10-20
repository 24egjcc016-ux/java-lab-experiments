package com.pulse.client;

import com.pulse.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The client side of the protocol. Connecting blocks until the server either
 * welcomes us or rejects the name, which is exactly what the login screen
 * needs: one call, one success or one readable error.
 *
 * <p>After that a reader thread turns incoming frames into callbacks. The
 * callbacks run on that reader thread, never on the JavaFX thread.</p>
 */
public class ChatClient implements AutoCloseable {

    public interface Listener {
        void onChat(String sender, String text);

        void onSystem(String text);

        void onUserList(List<String> usernames);

        void onDisconnected(String reason);
    }

    private static final Listener NO_OP = new Listener() {
        @Override public void onChat(String sender, String text) { }

        @Override public void onSystem(String text) { }

        @Override public void onUserList(List<String> usernames) { }

        @Override public void onDisconnected(String reason) { }
    };

    private final AtomicBoolean closed = new AtomicBoolean(true);

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread reader;
    private volatile Listener listener = NO_OP;
    private volatile String username;

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    /**
     * Opens the socket, sends JOIN and waits for the server's answer.
     *
     * @throws IOException when the host is unreachable or the server rejects
     *                     the connection. The message is safe to show in the UI.
     */
    public void connect(String host, int port, String name) throws IOException {
        Socket fresh = new Socket();
        try {
            fresh.connect(new InetSocketAddress(host, port), 4000);
            fresh.setTcpNoDelay(true);
        } catch (IOException e) {
            closeQuietly(fresh, null, null);
            throw new IOException("Could not reach " + host + ":" + port);
        }

        DataInputStream freshIn = new DataInputStream(new BufferedInputStream(fresh.getInputStream()));
        DataOutputStream freshOut = new DataOutputStream(new BufferedOutputStream(fresh.getOutputStream()));

        try {
            freshOut.writeUTF(Protocol.frame(Protocol.JOIN, name));
            freshOut.flush();

            String answer = freshIn.readUTF();
            String[] parts = Protocol.split(answer);

            if (Protocol.ERROR.equals(parts[0])) {
                String reason = parts.length > 1 ? parts[1] : "Connection refused";
                closeQuietly(fresh, freshIn, freshOut);
                throw new IOException(reason);
            }
            if (!Protocol.WELCOME.equals(parts[0])) {
                closeQuietly(fresh, freshIn, freshOut);
                throw new IOException("Unexpected reply from the server");
            }

            socket = fresh;
            in = freshIn;
            out = freshOut;
            username = parts.length > 1 ? parts[1] : name;
            closed.set(false);
        } catch (EOFException e) {
            closeQuietly(fresh, freshIn, freshOut);
            throw new IOException("The server closed the connection");
        }

        reader = new Thread(this::readLoop, "pulse-chat-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public String getUsername() {
        return username;
    }

    public boolean isConnected() {
        return !closed.get() && socket != null && socket.isConnected();
    }

    public synchronized void sendMessage(String text) {
        write(Protocol.frame(Protocol.MSG, text));
    }

    public synchronized void requestUsers() {
        write(Protocol.frame(Protocol.USERS));
    }

    /**
     * Leaves politely: the QUIT frame lets the server drop us before the
     * socket closes, so other clients see a clean "left the chat" notice.
     */
    public synchronized void disconnect() {
        if (closed.get()) {
            return;
        }
        write(Protocol.frame(Protocol.QUIT));
        closed.set(true);
        closeQuietly(socket, in, out);
    }

    private void write(String frame) {
        if (out == null) {
            return;
        }
        try {
            out.writeUTF(frame);
            out.flush();
        } catch (IOException e) {
            fail("Lost connection to the server");
        }
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                String[] parts = Protocol.split(in.readUTF());
                switch (parts[0]) {
                    case Protocol.CHAT -> {
                        if (parts.length >= 3) {
                            listener.onChat(parts[1], parts[2]);
                        }
                    }
                    case Protocol.SYSTEM -> {
                        if (parts.length >= 2) {
                            listener.onSystem(parts[1]);
                        }
                    }
                    case Protocol.USERLIST -> listener.onUserList(readNames(parts));
                    case Protocol.ERROR -> fail(parts.length > 1 ? parts[1] : "Server error");
                    default -> {
                        // Forward compatible: ignore frames we do not know.
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            fail("The server closed the connection");
        } catch (IOException e) {
            fail("Lost connection to the server");
        }
    }

    private static List<String> readNames(String[] parts) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                names.add(parts[i]);
            }
        }
        return names;
    }

    private void fail(String reason) {
        if (closed.getAndSet(true)) {
            return;
        }
        closeQuietly(socket, in, out);
        listener.onDisconnected(reason);
    }

    private static void closeQuietly(Socket socket, DataInputStream in, DataOutputStream out) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
            // Closing twice is harmless.
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
            // Closing twice is harmless.
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Closing twice is harmless.
        }
    }

    @Override
    public void close() {
        disconnect();
    }
}
