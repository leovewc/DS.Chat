package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientGUI provides a JavaFX-based chat client interface.
 */
public class ClientGUI extends Application {
    private TextArea displayArea;
    private TextField inputField;
    private Button sendButton;
    private ComboBox<String> commandBox;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public void start(Stage primaryStage) {
        displayArea = new TextArea();
        displayArea.setEditable(false);
        displayArea.setWrapText(true);

        inputField = new TextField();
        inputField.setPromptText("Enter message or command...");
        inputField.setOnAction(e -> sendInput());

        sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendInput());

        commandBox = new ComboBox<>();
        commandBox.getItems().addAll("JOIN", "SEND", "LIST", "HISTORY", "QUIT");
        commandBox.setValue("SEND");

        HBox bottomBox = new HBox(5, commandBox, inputField, sendButton);
        bottomBox.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(new ScrollPane(displayArea));
        root.setBottom(bottomBox);

        primaryStage.setTitle("Distributed Chat Client");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();

        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 9999);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Read welcome message
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> displayArea.appendText(msg + "\n"));
                    // After welcome, break to allow user commands
                    if (msg.startsWith("Welcome!")) break;
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Connection Error", "无法连接到服务器: " + e.getMessage());
                });
            }
        }).start();
    }

    private void sendInput() {
        String cmd = commandBox.getValue();
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        String fullCmd;
        if (cmd.equals("SEND") && !text.startsWith(cmd)) {
            // Default SEND uses current room implicit if user includes room prefix
            fullCmd = "SEND " + text;
        } else if (cmd.equals("QUIT")) {
            fullCmd = "QUIT";
        } else {
            fullCmd = cmd + " " + text;
        }

        inputField.clear();
        displayArea.appendText("> " + fullCmd + "\n");

        new Thread(() -> {
            try {
                out.println(fullCmd);
                String response = in.readLine();
                Platform.runLater(() -> displayArea.appendText(response + "\n"));
                if (cmd.equals("QUIT")) {
                    socket.close();
                    Platform.runLater(() -> showAlert("Disconnected", "You have been disconnected."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Communication Error", e.getMessage()));
            }
        }).start();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (socket != null && !socket.isClosed()) {
            out.println("QUIT");
            socket.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
