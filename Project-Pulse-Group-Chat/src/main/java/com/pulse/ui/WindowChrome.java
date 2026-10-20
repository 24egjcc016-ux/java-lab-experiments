package com.pulse.ui;

import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Replaces the native title bar with one that matches the app, and adds the
 * resize behaviour that an undecorated window loses.
 *
 * <p>The window is opened with {@code StageStyle.UNDECORATED}, so the scene
 * itself has to provide dragging, minimising and closing. Edges are detected
 * with a 6px band around the scene.</p>
 */
public final class WindowChrome {

    private static final int EDGE = 6;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int TOP = 4;
    private static final int BOTTOM = 8;

    private static boolean resizing;
    private static int direction;
    private static double pressScreenX;
    private static double pressScreenY;
    private static double startX;
    private static double startY;
    private static double startWidth;
    private static double startHeight;

    private WindowChrome() {
    }

    /**
     * Builds the bar. {@code context} is the small grey word after the name,
     * for example "sign in", a username or "dashboard".
     */
    public static HBox bar(Stage stage, String context) {
        Label brand = new Label("Pulse");
        brand.getStyleClass().add("title-brand");

        Label separator = new Label("—");
        separator.getStyleClass().add("title-separator");

        Label where = new Label(context);
        where.getStyleClass().add("title-context");

        HBox dragArea = new HBox(8, brand, separator, where);
        dragArea.setAlignment(Pos.CENTER_LEFT);
        dragArea.setMinHeight(40);
        HBox.setHgrow(dragArea, Priority.ALWAYS);

        dragArea.setOnMousePressed(event -> {
            pressScreenX = event.getScreenX();
            pressScreenY = event.getScreenY();
            startX = stage.getX();
            startY = stage.getY();
        });

        dragArea.setOnMouseDragged(event -> {
            if (stage.isMaximized()) {
                return;
            }
            stage.setX(startX + event.getScreenX() - pressScreenX);
            stage.setY(startY + event.getScreenY() - pressScreenY);
        });

        dragArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
            }
        });

        Button minimise = new Button("–");
        minimise.getStyleClass().add("window-button");
        minimise.setTooltip(new Tooltip("Minimise"));
        minimise.setOnAction(event -> stage.setIconified(true));

        Button maximise = new Button("□");
        maximise.getStyleClass().add("window-button");
        maximise.setTooltip(new Tooltip("Maximise"));
        maximise.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));

        Button close = new Button("✕");
        close.getStyleClass().addAll("window-button", "window-close");
        close.setTooltip(new Tooltip("Close"));
        close.setOnAction(event -> {
            WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            Event.fireEvent(stage, request);
            if (!request.isConsumed()) {
                stage.hide();
            }
        });

        Region gap = new Region();
        gap.setMinWidth(2);

        HBox bar = new HBox(dragArea, gap, minimise, maximise, close);
        bar.getStyleClass().add("titlebar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(40);
        bar.setPrefHeight(40);
        return bar;
    }

    /**
     * Wraps a scene root with the title bar. The content keeps all the
     * remaining height.
     */
    public static VBox frame(Stage stage, String context, Region content) {
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox frame = new VBox(bar(stage, context), content);
        frame.getStyleClass().add("app-frame");
        return frame;
    }

    /**
     * Edge and corner resize for a stage without decorations.
     */
    public static void makeResizable(Scene scene, Stage stage) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (stage.isMaximized()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            scene.setCursor(cursorFor(directionAt(scene, event.getSceneX(), event.getSceneY())));
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
                return;
            }

            int edge = directionAt(scene, event.getSceneX(), event.getSceneY());
            if (edge == 0) {
                return;
            }

            resizing = true;
            direction = edge;
            pressScreenX = event.getScreenX();
            pressScreenY = event.getScreenY();
            startX = stage.getX();
            startY = stage.getY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!resizing) {
                return;
            }

            double dx = event.getScreenX() - pressScreenX;
            double dy = event.getScreenY() - pressScreenY;

            double x = startX;
            double y = startY;
            double width = startWidth;
            double height = startHeight;

            if ((direction & LEFT) != 0) {
                x = startX + dx;
                width = startWidth - dx;
            } else if ((direction & RIGHT) != 0) {
                width = startWidth + dx;
            }

            if ((direction & TOP) != 0) {
                y = startY + dy;
                height = startHeight - dy;
            } else if ((direction & BOTTOM) != 0) {
                height = startHeight + dy;
            }

            if (width < stage.getMinWidth()) {
                if ((direction & LEFT) != 0) {
                    x = startX + startWidth - stage.getMinWidth();
                }
                width = stage.getMinWidth();
            }
            if (height < stage.getMinHeight()) {
                if ((direction & TOP) != 0) {
                    y = startY + startHeight - stage.getMinHeight();
                }
                height = stage.getMinHeight();
            }

            stage.setX(x);
            stage.setY(y);
            stage.setWidth(width);
            stage.setHeight(height);
            event.consume();
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> resizing = false);
    }

    private static int directionAt(Scene scene, double x, double y) {
        int edge = 0;

        if (x <= EDGE) {
            edge |= LEFT;
        } else if (x >= scene.getWidth() - EDGE) {
            edge |= RIGHT;
        }

        if (y <= EDGE) {
            edge |= TOP;
        } else if (y >= scene.getHeight() - EDGE) {
            edge |= BOTTOM;
        }

        return edge;
    }

    private static Cursor cursorFor(int edge) {
        return switch (edge) {
            case LEFT -> Cursor.W_RESIZE;
            case RIGHT -> Cursor.E_RESIZE;
            case TOP -> Cursor.N_RESIZE;
            case BOTTOM -> Cursor.S_RESIZE;
            case LEFT | TOP -> Cursor.NW_RESIZE;
            case RIGHT | TOP -> Cursor.NE_RESIZE;
            case LEFT | BOTTOM -> Cursor.SW_RESIZE;
            case RIGHT | BOTTOM -> Cursor.SE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }
}
