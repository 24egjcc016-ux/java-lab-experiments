package com.pulse.server;

import com.pulse.ui.Avatar;
import com.pulse.ui.WindowChrome;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A small dashboard for the server: status, counters, the live log and the
 * list of connected clients. The networking itself lives in
 * {@link ChatServer}.
 */
public class ServerApp extends Application {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ChatServer server;
    private int port;
    private long startedAt;

    private TextArea log;
    private VBox clientList;
    private Label clientsCount;
    private Label onlineValue;
    private Label messageValue;
    private Label uptimeValue;
    private Label statusText;
    private HBox statusIndicator;
    private Button toggleButton;
    private Timeline uptimeClock;

    @Override
    public void start(Stage stage) {
        port = readPort();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/pulse/icon.png")));

        Label title = new Label("Pulse Server");
        title.getStyleClass().add("server-title");

        Label subtitle = new Label("TCP relay · port " + port);
        subtitle.getStyleClass().add("server-subtitle");

        VBox titles = new VBox(1, title, subtitle);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusIndicator = new HBox(7);
        statusIndicator.getStyleClass().add("status-indicator");
        statusIndicator.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(4.5);
        dot.getStyleClass().add("status-dot");

        statusText = new Label("Starting...");
        statusText.getStyleClass().add("status-text");

        statusIndicator.getChildren().addAll(dot, statusText);

        toggleButton = new Button("Stop server");
        toggleButton.getStyleClass().add("button-ghost");
        toggleButton.setOnAction(event -> toggleServer());

        HBox header = new HBox(12, titles, spacer, statusIndicator, toggleButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFillHeight(false);
        statusIndicator.setMaxHeight(Region.USE_PREF_SIZE);

        onlineValue = statValue("0");
        messageValue = statValue("0");
        uptimeValue = statValue("—");

        HBox stats = new HBox(
                statCell(onlineValue, "Online"),
                statDivider(),
                statCell(messageValue, "Messages"),
                statDivider(),
                statCell(uptimeValue, "Uptime"));
        stats.getStyleClass().add("stat-row");
        stats.setAlignment(Pos.CENTER_LEFT);

        log = new TextArea();
        log.setEditable(false);
        log.setWrapText(false);
        log.getStyleClass().add("log-view");
        VBox.setVgrow(log, Priority.ALWAYS);

        VBox logPanel = new VBox(10, panelTitle("Log"), log);
        logPanel.getStyleClass().add("panel");
        logPanel.setPadding(new Insets(14));
        HBox.setHgrow(logPanel, Priority.ALWAYS);

        clientsCount = new Label("0");
        clientsCount.getStyleClass().add("sidebar-count");

        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);

        HBox clientsHead = new HBox(panelTitle("Connected"), headSpacer, clientsCount);
        clientsHead.setAlignment(Pos.CENTER_LEFT);

        clientList = new VBox(1);
        clientList.getStyleClass().add("user-list");

        ScrollPane clientsScroll = new ScrollPane(clientList);
        clientsScroll.setFitToWidth(true);
        clientsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        clientsScroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(clientsScroll, Priority.ALWAYS);

        VBox clientsPanel = new VBox(10, clientsHead, clientsScroll);
        clientsPanel.getStyleClass().add("panel");
        clientsPanel.setPadding(new Insets(14));
        clientsPanel.setPrefWidth(250);
        clientsPanel.setMinWidth(250);
        clientsPanel.setMaxWidth(250);

        HBox content = new HBox(12, logPanel, clientsPanel);
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox root = new VBox(16, header, stats, content);
        root.getStyleClass().addAll("app-bg", "server-root");
        root.setPadding(new Insets(18));
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox frame = WindowChrome.frame(stage, "dashboard", root);

        Scene scene = new Scene(frame, 1040, 660);
        scene.getStylesheets().add(getClass().getResource("/com/pulse/app.css").toExternalForm());
        WindowChrome.makeResizable(scene, stage);

        stage.setScene(scene);
        stage.setTitle("Pulse Server");
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        startServer();

        uptimeClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateUptime()));
        uptimeClock.setCycleCount(Animation.INDEFINITE);
        uptimeClock.play();
    }

    private void startServer() {
        server = new ChatServer();
        server.setListener(new ServerListener());

        onlineValue.setText("0");
        messageValue.setText("0");
        clientList.getChildren().clear();
        clientsCount.setText("0");

        try {
            server.start(port);
            startedAt = System.currentTimeMillis();
            setRunning(true);
            appendLog("Server ready — waiting for clients");
        } catch (IOException e) {
            setRunning(false);
            appendLog("Could not bind port " + port + ": " + e.getMessage());
        }
    }

    private void toggleServer() {
        if (server != null && server.isRunning()) {
            server.stop();
            setRunning(false);
        } else {
            startServer();
        }
    }

    private void setRunning(boolean running) {
        statusText.setText(running ? "Listening on " + port : "Stopped");

        statusIndicator.getStyleClass().remove("stopped");
        if (!running) {
            statusIndicator.getStyleClass().add("stopped");
        }

        toggleButton.setText(running ? "Stop server" : "Start server");
        if (!running) {
            uptimeValue.setText("—");
        }
    }

    private void updateUptime() {
        if (server == null || !server.isRunning()) {
            uptimeValue.setText("—");
            return;
        }

        long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;

        if (hours > 0) {
            uptimeValue.setText(String.format("%d:%02d:%02d", hours, minutes, rest));
        } else {
            uptimeValue.setText(String.format("%02d:%02d", minutes, rest));
        }
    }

    private void appendLog(String line) {
        log.appendText(LocalTime.now().format(CLOCK) + "   " + line + "\n");
        log.setScrollTop(Double.MAX_VALUE);
    }

    private void renderClients(List<String> names) {
        clientList.getChildren().clear();
        clientsCount.setText(String.valueOf(names.size()));
        onlineValue.setText(String.valueOf(names.size()));

        if (names.isEmpty()) {
            Label empty = new Label("No clients yet");
            empty.getStyleClass().add("hint-text");
            empty.setPadding(new Insets(8));
            clientList.getChildren().add(empty);
            return;
        }

        for (String name : names) {
            Label label = new Label(name);
            label.getStyleClass().add("user-row-name");

            HBox row = new HBox(9, new Avatar(name, 22), label);
            row.getStyleClass().add("user-row");
            row.setAlignment(Pos.CENTER_LEFT);
            clientList.getChildren().add(row);
        }
    }

    private void shutdown() {
        if (uptimeClock != null) {
            uptimeClock.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    private Label statValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("stat-value");
        return label;
    }

    private VBox statCell(Label value, String caption) {
        Label label = new Label(caption);
        label.getStyleClass().add("stat-label");

        VBox cell = new VBox(2, value, label);
        cell.getStyleClass().add("stat-cell");
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    private Region statDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("stat-divider");
        HBox.setMargin(divider, new Insets(0, 24, 0, 24));
        return divider;
    }

    private Label panelTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }

    private static int readPort() {
        String value = System.getenv("PULSE_PORT");
        if (value != null && !value.isBlank()) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0 && parsed < 65536) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the default below.
            }
        }
        return 5000;
    }

    public static void main(String[] args) {
        launch(args);
    }

    private class ServerListener implements ChatServer.Listener {
        @Override
        public void onLog(String line) {
            onUi(() -> appendLog(line));
        }

        @Override
        public void onClientsChanged(List<String> usernames) {
            onUi(() -> renderClients(usernames));
        }

        @Override
        public void onMessageCount(int total) {
            onUi(() -> messageValue.setText(String.valueOf(total)));
        }

        /**
         * Network threads must hop to the JavaFX thread. Startup calls
         * already arrive on it, and running them directly keeps the first
         * two log lines in the order they happened.
         */
        private void onUi(Runnable task) {
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        }
    }
}
