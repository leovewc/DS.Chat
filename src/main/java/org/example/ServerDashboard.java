package org.example;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ServerDashboard provides a JavaFX-based admin interface:
 * - Displays active rooms and client count
 * - Shows recent server logs
 * - Allows manual backup trigger
 */
public class ServerDashboard extends Application {

    private ListView<String> roomsList;
    private Label clientCountLabel;
    private TextArea logArea;
    private Button backupButton;

    private ScheduledExecutorService refresher;

    @Override
    public void start(Stage primaryStage) {
        roomsList = new ListView<>();
        clientCountLabel = new Label("Clients: 0");
        logArea = new TextArea();
        logArea.setEditable(false);

        backupButton = new Button("Backup Now");
        backupButton.setOnAction(e -> triggerBackup());

        VBox leftPane = new VBox(10, new Label("Active Rooms"), roomsList, clientCountLabel, backupButton);
        leftPane.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setLeft(leftPane);
        root.setCenter(new ScrollPane(logArea));

        primaryStage.setTitle("Server Dashboard");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();

        startRefresher();
    }

    /**
     * Periodically refresh stats and logs
     */
    private void startRefresher() {
        refresher = Executors.newSingleThreadScheduledExecutor();
        refresher.scheduleAtFixedRate(() -> {
    int clientCount = ServerStats.getActiveClientCount();
    List<String> rooms = ServerStats.getActiveRooms();
    List<String> logs = ServerStats.getRecentLogs();

    Platform.runLater(() -> {
        clientCountLabel.setText("Clients: " + clientCount);
        roomsList.getItems().setAll(rooms);
        logArea.clear();
        logs.forEach(line -> logArea.appendText(line + "\n"));
    });
}, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Send manual backup command to server, handling IOException
     */
    private void triggerBackup() {
        new Thread(() -> {
            try {
                MessageHelper.backupHistory();
                Platform.runLater(() -> logArea.appendText("[Dashboard] Manual backup triggered.\n"));
            } catch (IOException e) {
                Platform.runLater(() -> logArea.appendText("[Dashboard] Backup failed: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        refresher.shutdown();
    }

    public static void main(String[] args) {
        // —— 1. 在同一 JVM 中启动聊天服务器 ——
        Thread serverThread = new Thread(() -> {
            try {
                // 调用你的 Server.main 来开启 accept 循环、调度等逻辑
                Server.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);  // 服务器线程随 Dashboard 退出而退出
        serverThread.start();

        // —— 2. 启动 JavaFX UI ——
        launch(args);
    }
}

