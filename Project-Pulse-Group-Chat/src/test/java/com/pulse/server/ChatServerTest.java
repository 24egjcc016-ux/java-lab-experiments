package com.pulse.server;

import com.pulse.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives the real server over real sockets. Port 0 lets the OS pick a free
 * port, so the tests do not clash with a server the developer already has
 * running.
 */
@Timeout(10)
class ChatServerTest {

    private ChatServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer();
        server.start(0);
        port = server.getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void greetingAndDuplicateNames() throws Exception {
        try (TestClient alice = new TestClient(port, "alice");
             TestClient impostor = new TestClient(port, "ALICE")) {

            assertEquals(Protocol.WELCOME, Protocol.typeOf(alice.read()));

            String answer = impostor.read();
            assertEquals(Protocol.ERROR, Protocol.typeOf(answer));
            assertTrue(answer.toLowerCase().contains("taken"));
        }
    }

    @Test
    void shortNamesAreRejected() throws Exception {
        try (TestClient tiny = new TestClient(port, "a")) {
            assertEquals(Protocol.ERROR, Protocol.typeOf(tiny.read()));
        }
    }

    @Test
    void messageIsDeliveredToEveryone() throws Exception {
        try (TestClient alice = new TestClient(port, "alice");
             TestClient bob = new TestClient(port, "bob")) {

            alice.readUntil(Protocol.WELCOME);
            bob.readUntil(Protocol.WELCOME);

            bob.send(Protocol.MSG, "hello from bob");

            String[] atAlice = Protocol.split(alice.readUntil(Protocol.CHAT));
            assertEquals("bob", atAlice[1]);
            assertEquals("hello from bob", atAlice[2]);

            String[] atBob = Protocol.split(bob.readUntil(Protocol.CHAT));
            assertEquals("bob", atBob[1]);
            assertEquals("hello from bob", atBob[2]);
        }
    }

    @Test
    void userListContainsBothNames() throws Exception {
        try (TestClient alice = new TestClient(port, "alice");
             TestClient bob = new TestClient(port, "bob")) {

            alice.readUntil(Protocol.WELCOME);
            bob.readUntil(Protocol.WELCOME);

            alice.send(Protocol.USERS);
            List<String> names = namesOf(alice.readUserListContaining("bob"));

            assertTrue(names.contains("alice"), "alice should be listed");
            assertTrue(names.contains("bob"), "bob should be listed");
        }
    }

    @Test
    void leavingIsAnnouncedToTheOthers() throws Exception {
        try (TestClient alice = new TestClient(port, "alice");
             TestClient bob = new TestClient(port, "bob")) {

            alice.readUntil(Protocol.WELCOME);
            bob.readUntil(Protocol.WELCOME);

            bob.close();

            String notice = alice.readSystemContaining("left");
            assertTrue(notice.contains("bob"), "notice was: " + notice);
        }
    }

    private static List<String> namesOf(String frame) {
        List<String> names = new ArrayList<>();
        String[] parts = Protocol.split(frame);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                names.add(parts[i]);
            }
        }
        return names;
    }

    /**
     * A deliberately small client for the tests. It talks the same protocol
     * as the real one but without any of the UI code.
     */
    private static final class TestClient implements Closeable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        TestClient(int port, String name) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(5000);

            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            send(Protocol.JOIN, name);
        }

        void send(String type, String... fields) throws IOException {
            out.writeUTF(Protocol.frame(type, fields));
            out.flush();
        }

        String read() throws IOException {
            return in.readUTF();
        }

        /**
         * Reads frames until one has the wanted type. Keeps the tests from
         * depending on the exact order of system notices and user lists.
         */
        String readUntil(String type) throws IOException {
            for (int i = 0; i < 30; i++) {
                String frame = in.readUTF();
                if (Protocol.typeOf(frame).equals(type)) {
                    return frame;
                }
            }
            return fail("Never received a " + type + " frame");
        }

        /**
         * Join notices and user lists arrive in bursts, so the first matching
         * frame can still be about an earlier event. These two helpers skip
         * ahead to the frame the test actually cares about.
         */
        String readUserListContaining(String name) throws IOException {
            for (int i = 0; i < 30; i++) {
                String frame = readUntil(Protocol.USERLIST);
                if (namesOf(frame).contains(name)) {
                    return frame;
                }
            }
            return fail("Never received a user list containing " + name);
        }

        String readSystemContaining(String text) throws IOException {
            for (int i = 0; i < 30; i++) {
                String frame = readUntil(Protocol.SYSTEM);
                if (frame.toLowerCase().contains(text.toLowerCase())) {
                    return frame;
                }
            }
            return fail("Never received a system frame containing " + text);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
