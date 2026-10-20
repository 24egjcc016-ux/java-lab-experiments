package com.pulse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {

    @Test
    void frameKeepsFieldsWithSpacesAndCommas() {
        String frame = Protocol.frame(Protocol.CHAT, "alice", "hello, world. how are you?");
        String[] parts = Protocol.split(frame);

        assertEquals(3, parts.length);
        assertEquals(Protocol.CHAT, parts[0]);
        assertEquals("alice", parts[1]);
        assertEquals("hello, world. how are you?", parts[2]);
    }

    @Test
    void splitKeepsEmptyFields() {
        String frame = Protocol.frame(Protocol.USERLIST, "", "bob", "");
        String[] parts = Protocol.split(frame);

        assertEquals(4, parts.length);
        assertEquals("", parts[1]);
        assertEquals("bob", parts[2]);
        assertEquals("", parts[3]);
    }

    @Test
    void newlinesSurviveTheRoundTrip() {
        String text = "first line\nsecond line";
        String frame = Protocol.frame(Protocol.MSG, text);

        assertEquals(text, Protocol.split(frame)[1]);
    }

    @Test
    void typeOfWorksWithAndWithoutPayload() {
        assertEquals(Protocol.USERS, Protocol.typeOf(Protocol.frame(Protocol.USERS)));
        assertEquals(Protocol.JOIN, Protocol.typeOf(Protocol.frame(Protocol.JOIN, "alice")));
    }

    @Test
    void aMessageThatLooksLikeAnotherFrameStaysInItsOwnField() {
        String tricky = Protocol.frame(Protocol.QUIT);
        String frame = Protocol.frame(Protocol.MSG, tricky);

        String[] parts = Protocol.split(frame);
        assertEquals(2, parts.length);
        assertEquals(Protocol.MSG, parts[0]);
        assertEquals(tricky, parts[1]);
        assertTrue(parts[1].contains(Protocol.QUIT));
    }
}
