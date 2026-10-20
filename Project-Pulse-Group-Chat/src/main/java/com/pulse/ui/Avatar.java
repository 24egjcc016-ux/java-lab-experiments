package com.pulse.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * A small circle with the user's initials. The circle takes a faint tint of
 * the person's colour and the initials the colour itself. Sized by the caller
 * so the same component works in the message list, the member list and the
 * server dashboard.
 */
public class Avatar extends StackPane {

    public Avatar(String username, double size) {
        Circle circle = new Circle(size / 2);
        circle.getStyleClass().addAll("avatar-circle", Theme.userStyleClass(username));

        Label initials = new Label(Theme.initials(username));
        initials.getStyleClass().add("avatar-initials");
        Theme.applyUserColour(initials, username);
        initials.setStyle("-fx-font-size: " + Math.round(size * 0.40) + "px;");

        getChildren().addAll(circle, initials);

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
    }
}
