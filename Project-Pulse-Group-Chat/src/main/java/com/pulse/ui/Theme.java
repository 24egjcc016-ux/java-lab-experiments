package com.pulse.ui;

import javafx.scene.Node;

/**
 * Small helpers shared by the client and the server screens.
 *
 * <p>Avatar colours live in the stylesheet as {@code .user-0} to
 * {@code .user-7}. This class only decides which one a name gets, so the
 * same person keeps the same colour on every screen.</p>
 */
public final class Theme {

    private static final int USER_COLOURS = 8;

    private Theme() {
    }

    public static String userStyleClass(String username) {
        int index = Math.floorMod(username.toLowerCase().hashCode(), USER_COLOURS);
        return "user-" + index;
    }

    public static void applyUserColour(Node node, String username) {
        node.getStyleClass().add(userStyleClass(username));
    }

    /**
     * One letter for a normal name, two when the name has an underscore
     * ("java_fan" becomes "JF").
     */
    public static String initials(String username) {
        if (username == null || username.isEmpty()) {
            return "?";
        }

        StringBuilder initials = new StringBuilder();
        initials.append(Character.toUpperCase(username.charAt(0)));

        int underscore = username.indexOf('_');
        if (underscore >= 0 && underscore + 1 < username.length()) {
            initials.append(Character.toUpperCase(username.charAt(underscore + 1)));
        }

        return initials.toString();
    }
}
