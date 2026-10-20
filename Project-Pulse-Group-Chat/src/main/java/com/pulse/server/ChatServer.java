package com.pulse.server;

import com.pulse.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The chat server. It accepts TCP connections, keeps one
 * {@link ClientHandler} per connection and relays messages to everyone.
 *
 * <p>The class has no JavaFX imports on purpose. {@link ServerApp} is only a
 * viewer on top of it, and the tests drive it directly.</p>
 */
public class ChatServer implements AutoCloseable {

    /**
     * Called from network threads. The UI is responsible for hopping back to
     * the JavaFX thread with {@code Platform.runLater}.
     */
    public interface Listener {
        void onLog(String line);

        void onClientsChanged(List<String> usernames);

        void onMessageCount(int total);
    }

    private static final Listener NO_OP = new Listener() {
        @Override public void onLog(String line) { }

        @Override public void onClientsChanged(List<String> usernames) { }

        @Override public void onMessageCount(int total) { }
    };

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicInteger messageCount = new AtomicInteger();
    private final AtomicInteger connectionIds = new AtomicInteger();

    private volatile Listener listener = NO_OP;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    /**
     * Binds the port and starts accepting connections. Pass 0 to let the OS
     * pick a free port, then read it back with {@link #getPort()}.
     */
    public synchronized void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));

        serverSocket = socket;
        running = true;

        Thread acceptor = new Thread(this::acceptLoop, "pulse-accept");
        acceptor.setDaemon(true);
        acceptor.start();

        log("Listening on port " + getPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                ClientHandler handler = new ClientHandler(this, socket, connectionIds.incrementAndGet());
                Thread worker = new Thread(handler, "pulse-client-" + handler.getId());
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (running) {
                    log("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    public int getMessageCount() {
        return messageCount.get();
    }

    public int getOnlineCount() {
        return clients.size();
    }

    /**
     * Display names of everyone currently connected, sorted for the UI.
     */
    public List<String> getUsernames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler handler : clients.values()) {
            String name = handler.getUsername();
            if (name != null) {
                names.add(name);
            }
        }
        names.sort(Comparator.comparing(String::toLowerCase));
        return names;
    }

    // --- used by ClientHandler ---

    boolean register(String key, ClientHandler handler) {
        return clients.putIfAbsent(key, handler) == null;
    }

    void unregister(String key) {
        clients.remove(key);
    }

    void broadcast(String frame) {
        for (ClientHandler handler : clients.values()) {
            handler.send(frame);
        }
    }

    /**
     * Everyone except one client. Used for join notices so you do not get a
     * message telling you that you joined.
     */
    void broadcastExcept(ClientHandler skip, String frame) {
        for (ClientHandler handler : clients.values()) {
            if (handler != skip) {
                handler.send(frame);
            }
        }
    }

    void broadcastUserList() {
        String[] names = getUsernames().toArray(new String[0]);
        broadcast(Protocol.frame(Protocol.USERLIST, names));
    }

    void countMessage() {
        int total = messageCount.incrementAndGet();
        listener.onMessageCount(total);
    }

    void log(String line) {
        listener.onLog(line);
    }

    void clientsChanged() {
        listener.onClientsChanged(getUsernames());
        broadcastUserList();
    }

    /**
     * Closes the listening socket and every open connection.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // Closing a server socket that is already gone is fine.
        }

        for (ClientHandler handler : clients.values()) {
            handler.close();
        }
        clients.clear();

        log("Server stopped");
        clientsChanged();
    }

    @Override
    public void close() {
        stop();
    }
}
