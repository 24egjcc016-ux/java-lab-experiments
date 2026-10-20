package com.pulse.client;

import com.pulse.ui.Avatar;
import com.pulse.ui.Theme;
import com.pulse.ui.WindowChrome;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The chat window. It owns the login screen and the chat screen and swaps
 * between them on one stage.
 */
public class ClientApp extends Application {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int WINDOW_WIDTH = 1020;
    private static final int WINDOW_HEIGHT = 680;

    private Stage stage;
    private ChatClient client;

    private TextField nameField;
    private TextField hostField;
    private TextField portField;
    private Label loginError;
    private Button joinButton;

    private VBox messages;
    private ScrollPane messageScroll;
    private VBox emptyState;
    private TextArea composer;
    private VBox userList;
    private Label onlineCount;
    private Label channelSubtitle;
    private HBox connectionStatus;
    private Label connectionText;
    private Label banner;
    private Button leaveButton;

    /** Lower-case name of the person who sent the previous message. */
    private String lastSenderKey;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/pulse/icon.png")));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(event -> {
            if (client != null) {
                client.disconnect();
            }
        });

        showLogin();
        stage.show();

        if (!env("PULSE_NAME", "").isBlank()) {
            Platform.runLater(this::attemptJoin);
        }
    }

    // ---------------------------------------------------------------- login

    private void showLogin() {
        if (client != null) {
            client.disconnect();
            client = null;
        }

        Label heading = new Label("Sign in to Pulse");
        heading.getStyleClass().add("login-title");

        Label sub = new Label("Pick a name and join the room.");
        sub.getStyleClass().add("login-sub");

        VBox brand = new VBox(4, heading, sub);

        nameField = new TextField(env("PULSE_NAME", ""));
        nameField.setPromptText("e.g. alice");

        hostField = new TextField(env("PULSE_HOST", "localhost"));
        HBox.setHgrow(hostField, Priority.ALWAYS);

        portField = new TextField(env("PULSE_PORT", "5000"));
        portField.setPrefWidth(88);
        portField.setMaxWidth(88);

        HBox serverRow = new HBox(10, field("Host", hostField), field("Port", portField));

        loginError = new Label();
        loginError.getStyleClass().add("error-text");
        loginError.setWrapText(true);
        loginError.setManaged(false);
        loginError.setVisible(false);

        joinButton = new Button("Join chat");
        joinButton.getStyleClass().add("button-primary");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setDefaultButton(true);
        joinButton.setOnAction(event -> attemptJoin());

        Label hint = new Label("Start the server first:  run-server.bat");
        hint.getStyleClass().add("hint-text");

        VBox card = new VBox(14, brand, field("Display name", nameField), serverRow,
                loginError, joinButton, hint);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(360);

        VBox column = new VBox(card);
        column.setAlignment(Pos.CENTER);

        StackPane content = new StackPane(column);
        content.getStyleClass().add("app-bg");
        content.setPadding(new Insets(28));

        VBox frame = WindowChrome.frame(stage, "sign in", content);

        Scene scene = new Scene(frame, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(stylesheet());
        WindowChrome.makeResizable(scene, stage);
        stage.setScene(scene);
        stage.setTitle("Pulse — sign in");
        nameField.requestFocus();
    }

    private VBox field(String caption, TextField input) {
        Label label = new Label(caption);
        label.getStyleClass().add("field-label");
        return new VBox(6, label, input);
    }

    private void attemptJoin() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String host = hostField.getText() == null ? "" : hostField.getText().trim();

        if (name.isEmpty()) {
            showLoginError("Pick a display name first.");
            nameField.requestFocus();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showLoginError("Port has to be a number between 1 and 65535.");
            portField.requestFocus();
            return;
        }

        if (host.isEmpty()) {
            showLoginError("Enter the address of the server.");
            hostField.requestFocus();
            return;
        }

        hideLoginError();
        joinButton.setDisable(true);
        joinButton.setText("Connecting...");

        ChatClient next = new ChatClient();
        next.setListener(new ClientListener());

        Thread connect = new Thread(() -> {
            try {
                next.connect(host, port, name);
                Platform.runLater(() -> {
                    client = next;
                    showChat();
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    showLoginError(e.getMessage());
                    joinButton.setDisable(false);
                    joinButton.setText("Join chat");
                });
            }
        }, "pulse-connect");
        connect.setDaemon(true);
        connect.start();
    }

    private void showLoginError(String message) {
        loginError.setText(message);
        loginError.setManaged(true);
        loginError.setVisible(true);
    }

    private void hideLoginError() {
        loginError.setManaged(false);
        loginError.setVisible(false);
    }

    // ----------------------------------------------------------------- chat

    private void showChat() {
        leaveButton = new Button("Leave");
        leaveButton.getStyleClass().add("button-quiet");
        leaveButton.setOnAction(event -> leaveChat());

        HBox header = buildHeader();

        banner = new Label();
        banner.getStyleClass().add("banner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setManaged(false);
        banner.setVisible(false);

        messageScroll = new ScrollPane();
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScroll.getStyleClass().add("message-scroll");

        // Row spacing is carried by the rows themselves, so a run of messages
        // from one person can sit tight while a new sender opens a gap.
        messages = new VBox(0);
        messages.getStyleClass().add("messages");
        messages.setFillWidth(true);
        messageScroll.setContent(messages);

        emptyState = buildEmptyState();
        StackPane chatArea = new StackPane(messageScroll, emptyState);
        StackPane.setAlignment(emptyState, Pos.CENTER);
        emptyState.setMouseTransparent(true);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        composer = new TextArea();
        composer.getStyleClass().add("composer-input");
        composer.setPromptText("Message #general");
        composer.setWrapText(true);
        composer.setPrefRowCount(1);
        composer.setPrefHeight(36);
        composer.setMinHeight(36);
        composer.setMaxHeight(96);
        composer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                sendMessage();
            }
        });

        Button send = new Button("Send");
        send.getStyleClass().add("button-ghost");
        send.setOnAction(event -> sendMessage());

        HBox composerBox = new HBox(10, composer, send);
        composerBox.setAlignment(Pos.BOTTOM_RIGHT);
        composerBox.getStyleClass().add("composer-box");
        HBox.setHgrow(composer, Priority.ALWAYS);

        VBox composerWrap = new VBox(composerBox);
        composerWrap.getStyleClass().add("composer");

        VBox main = new VBox(header, banner, chatArea, composerWrap);
        main.getStyleClass().add("chat-main");
        HBox.setHgrow(main, Priority.ALWAYS);

        VBox sidebar = buildSidebar();

        HBox body = new HBox(main, sidebar);
        body.getStyleClass().add("chat-root");

        VBox frame = WindowChrome.frame(stage, client.getUsername(), body);

        Scene scene = new Scene(frame, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(stylesheet());
        WindowChrome.makeResizable(scene, stage);

        stage.setScene(scene);
        stage.setTitle("Pulse — " + client.getUsername());
        composer.requestFocus();

        // Every new row grows the VBox, so this keeps the view pinned to
        // the newest message without a timer.
        messages.heightProperty().addListener((observable, oldValue, newValue) ->
                messageScroll.setVvalue(1.0));

        lastSenderKey = null;

        if (client != null) {
            client.requestUsers();
        }
    }

    private HBox buildHeader() {
        Label channel = new Label("#general");
        channel.getStyleClass().add("channel-name");

        channelSubtitle = new Label("connecting...");
        channelSubtitle.getStyleClass().add("channel-subtitle");

        VBox titles = new VBox(1, channel, channelSubtitle);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        connectionStatus = new HBox(7);
        connectionStatus.getStyleClass().add("status-indicator");
        connectionStatus.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(4.5);
        dot.getStyleClass().add("status-dot");

        connectionText = new Label("Connected");
        connectionText.getStyleClass().add("status-text");

        connectionStatus.getChildren().addAll(dot, connectionText);

        HBox header = new HBox(12, titles, spacer, connectionStatus, leaveButton);
        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFillHeight(false);
        connectionStatus.setMaxHeight(Region.USE_PREF_SIZE);
        return header;
    }

    private VBox buildEmptyState() {
        Label title = new Label("Welcome to #general");
        title.getStyleClass().add("empty-title");

        Label text = new Label("Say hello. Everyone connected to this server will see it.");
        text.getStyleClass().add("empty-text");
        text.setWrapText(true);
        text.setMaxWidth(320);
        text.setAlignment(Pos.CENTER);

        VBox box = new VBox(8, title, text);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private VBox buildSidebar() {
        Label title = new Label("Online");
        title.getStyleClass().add("sidebar-title");

        onlineCount = new Label("—");
        onlineCount.getStyleClass().add("sidebar-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox head = new HBox(title, spacer, onlineCount);
        head.setAlignment(Pos.CENTER_LEFT);

        userList = new VBox(1);
        userList.getStyleClass().add("user-list");

        ScrollPane userScroll = new ScrollPane(userList);
        userScroll.setFitToWidth(true);
        userScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userScroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(userScroll, Priority.ALWAYS);

        Label me = new Label(client.getUsername());
        me.getStyleClass().add("profile-name");

        Label you = new Label("you");
        you.getStyleClass().add("you-tag");

        HBox profile = new HBox(10, new Avatar(client.getUsername(), 26), me, you);
        profile.getStyleClass().add("profile");
        profile.setAlignment(Pos.CENTER_LEFT);

        VBox sidebar = new VBox(12, head, userScroll, profile);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(248);
        sidebar.setMinWidth(248);
        sidebar.setMaxWidth(248);
        return sidebar;
    }

    private void sendMessage() {
        if (composer == null || client == null || !client.isConnected()) {
            return;
        }
        String text = composer.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        client.sendMessage(text);
        composer.clear();
        composer.requestFocus();
    }

    /**
     * One row per message, the layout every work chat uses: avatar, name,
     * time, then the text at full width. A run of messages from the same
     * person keeps one header and stacks the text below it.
     */
    private void appendChat(String sender, String text) {
        if (messages == null) {
            return;
        }
        hideEmptyState();

        boolean own = sender.equalsIgnoreCase(client.getUsername());
        String key = sender.toLowerCase();

        Label body = new Label(text);
        body.setWrapText(true);
        body.setMaxWidth(620);
        body.getStyleClass().add("message-text");

        HBox row = new HBox(10);
        row.getStyleClass().add("message-row");
        row.setAlignment(Pos.TOP_LEFT);

        if (key.equals(lastSenderKey)) {
            Region indent = new Region();
            indent.setMinWidth(26);
            indent.setPrefWidth(26);
            indent.setMaxWidth(26);
            indent.setMinHeight(1);
            indent.setPrefHeight(1);

            row.getStyleClass().add("grouped");
            row.getChildren().addAll(indent, body);
        } else {
            Label author = new Label(own ? "You" : sender);
            author.getStyleClass().add("message-author");
            Theme.applyUserColour(author, sender);

            Label time = new Label(LocalTime.now().format(CLOCK));
            time.getStyleClass().add("message-time");

            HBox meta = new HBox(8, author, time);
            meta.setAlignment(Pos.CENTER_LEFT);

            VBox block = new VBox(2, meta, body);
            HBox.setHgrow(block, Priority.ALWAYS);
            row.getChildren().addAll(new Avatar(sender, 26), block);
        }

        lastSenderKey = key;
        messages.getChildren().add(row);
        messageScroll.setVvalue(1.0);
    }

    private void appendSystem(String text) {
        if (messages == null) {
            return;
        }
        hideEmptyState();

        // A notice breaks the run, so the next message opens a new group.
        lastSenderKey = null;

        Label note = new Label(text);
        note.getStyleClass().add("system-note");

        HBox row = new HBox(note);
        row.setAlignment(Pos.CENTER);

        messages.getChildren().add(row);
    }

    private void hideEmptyState() {
        if (emptyState != null && emptyState.isVisible()) {
            emptyState.setVisible(false);
        }
    }

    private void renderUsers(List<String> names) {
        if (userList == null || client == null) {
            return;
        }

        userList.getChildren().clear();

        for (String name : names) {
            Label label = new Label(name);
            label.getStyleClass().add("user-row-name");

            HBox row = new HBox(9, new Avatar(name, 20), label);
            row.getStyleClass().add("user-row");
            row.setAlignment(Pos.CENTER_LEFT);

            if (name.equalsIgnoreCase(client.getUsername())) {
                Label you = new Label("you");
                you.getStyleClass().add("you-tag");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.getChildren().addAll(spacer, you);
            }

            userList.getChildren().add(row);
        }

        int count = names.size();
        onlineCount.setText(String.valueOf(count));
        channelSubtitle.setText(count + (count == 1 ? " person online" : " people online"));
    }

    private void handleDisconnect(String reason) {
        if (connectionText == null) {
            return;
        }
        connectionText.setText("Offline");
        if (!connectionStatus.getStyleClass().contains("offline")) {
            connectionStatus.getStyleClass().add("offline");
        }

        banner.setText(reason + "  ·  leave and join again to reconnect");
        banner.setManaged(true);
        banner.setVisible(true);

        if (composer != null) {
            composer.setDisable(true);
        }
    }

    private void leaveChat() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        showLogin();
    }

    private String stylesheet() {
        return getClass().getResource("/com/pulse/app.css").toExternalForm();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * ChatClient calls these from its reader thread, so every UI change is
     * wrapped in Platform.runLater.
     */
    private class ClientListener implements ChatClient.Listener {
        @Override
        public void onChat(String sender, String text) {
            Platform.runLater(() -> appendChat(sender, text));
        }

        @Override
        public void onSystem(String text) {
            Platform.runLater(() -> appendSystem(text));
        }

        @Override
        public void onUserList(List<String> usernames) {
            Platform.runLater(() -> renderUsers(usernames));
        }

        @Override
        public void onDisconnected(String reason) {
            Platform.runLater(() -> handleDisconnect(reason));
        }
    }
}
