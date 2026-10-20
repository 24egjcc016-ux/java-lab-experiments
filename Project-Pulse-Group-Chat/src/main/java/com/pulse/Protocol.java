package com.pulse;

/**
 * Wire format for the chat protocol.
 *
 * <p>Every frame is a single UTF string. The first field is the frame type,
 * the rest are payload fields. Fields are separated by the ASCII unit
 * separator (0x1F), a control character that will not appear in a normal
 * chat message. That way messages can contain spaces, commas and anything
 * else without escaping rules.</p>
 *
 * <p>Client to server: {@code JOIN}, {@code MSG}, {@code USERS}, {@code QUIT}.<br>
 * Server to client: {@code WELCOME}, {@code CHAT}, {@code SYSTEM},
 * {@code USERLIST}, {@code ERROR}.</p>
 */
public final class Protocol {

    /** ASCII unit separator. */
    public static final char SEP = '\u001F';

    // client -> server
    public static final String JOIN = "JOIN";
    public static final String MSG = "MSG";
    public static final String USERS = "USERS";
    public static final String QUIT = "QUIT";

    // server -> client
    public static final String WELCOME = "WELCOME";
    public static final String CHAT = "CHAT";
    public static final String SYSTEM = "SYSTEM";
    public static final String USERLIST = "USERLIST";
    public static final String ERROR = "ERROR";

    private Protocol() {
    }

    /**
     * Builds a frame from a type and zero or more fields.
     */
    public static String frame(String type, String... fields) {
        StringBuilder sb = new StringBuilder(type);
        for (String field : fields) {
            sb.append(SEP).append(field == null ? "" : field);
        }
        return sb.toString();
    }

    /**
     * Splits a frame into its fields. The type stays at index 0.
     * Empty trailing fields are kept so indexes stay predictable.
     */
    public static String[] split(String frame) {
        return frame.split(String.valueOf(SEP), -1);
    }

    /**
     * Returns just the frame type without splitting the payload.
     */
    public static String typeOf(String frame) {
        int sep = frame.indexOf(SEP);
        return sep < 0 ? frame : frame.substring(0, sep);
    }
}
