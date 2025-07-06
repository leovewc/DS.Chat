package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;



/**
 * QQ-style chat client with message bubbles.
 */
public class ClientGUI extends Application {
    private ListView<HBox> messageList;
    private TextField inputField;
    private Button sendButton;
    private ComboBox<String> commandBox;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    @Override
    public void start(Stage primaryStage) {
        // ─── Message list view ─────────────────────────────────────────
        messageList = new ListView<>();
        messageList.setFocusTraversable(false);
        messageList.setMouseTransparent(true);
        messageList.setSelectionModel(new NoSelectionModel<>());
        BorderPane.setMargin(messageList, new Insets(10));

        // ─── Bottom input bar ──────────────────────────────────────────
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

        // ─── Assemble root pane ────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setCenter(messageList);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 600, 400);
        // ─── Load CSS for bubbles ─────────────────────────────────────
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("Distributed Chat Client");
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 9999);
                out    = new PrintWriter(socket.getOutputStream(), true);
                in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 1) Read welcome lines
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> addMessage(msg, false));
                    if (msg.startsWith("Welcome!")) break;
                }

                // 2) Start listener for all future messages
                new Thread(() -> {
                    try {
                        String incoming;
                        while ((incoming = in.readLine()) != null) {
                            final String msg = incoming;
                            Platform.runLater(() -> addMessage(msg, false));
                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("Disconnected", "Lost connection to server."));
                    }
                }, "ServerListener").start();

            } catch (Exception e) {
                Platform.runLater(() ->
                    showAlert("Connection Error", "Cannot connect to server:\n" + e.getMessage())
                );
            }
        }, "Connector").start();
    }

    private void sendInput() {
        String cmd  = commandBox.getValue();
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        String fullCmd;
        if (cmd.equals("SEND") && !text.startsWith(cmd)) {
            fullCmd = "SEND " + text;
        } else if (cmd.equals("QUIT")) {
            fullCmd = "QUIT";
        } else {
            fullCmd = cmd + " " + text;
        }

        inputField.clear();
        addMessage("> " + fullCmd, true);

        new Thread(() -> {
            try {
                out.println(fullCmd);
                // read immediate response (e.g. JOIN ack, LIST/HISTORY result)
                String resp = in.readLine();
                if (resp != null) {
                    Platform.runLater(() -> addMessage(resp, false));
                }
                if (cmd.equals("QUIT")) {
                    socket.close();
                    Platform.runLater(() -> showAlert("Disconnected", "You have been disconnected."));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Communication Error", e.getMessage()));
            }
        }).start();
    }

    private void addMessage(String msg, boolean isLocal) {
        // Determine avatar and bubble alignment
        ImageView avatar = new ImageView(new Image(
            isLocal ? "/avatars/me.png" : "/avatars/other.png",
            32, 32, true, true
        ));
        Text text = new Text(msg);
        text.getStyleClass().add("chat-bubble-text");

        VBox bubble = new VBox(text);
        bubble.getStyleClass().add(isLocal ? "my-bubble" : "other-bubble");
        bubble.setMaxWidth(360);

        HBox row = new HBox(8);
        if (isLocal) {
            row.setAlignment(Pos.TOP_RIGHT);
            row.getChildren().addAll(bubble, avatar);
        } else {
            row.setAlignment(Pos.TOP_LEFT);
            row.getChildren().addAll(avatar, bubble);
        }

        messageList.getItems().add(row);
        messageList.scrollTo(messageList.getItems().size() - 1);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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

    /** A no-selection model to disable row selection in the ListView. */
    private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {
        @Override
        public ObservableList<Integer> getSelectedIndices() {
            // always empty
            return FXCollections.emptyObservableList();
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            // always empty
            return FXCollections.emptyObservableList();
        }
        @Override public void select(int index) { }
        @Override public void select(T obj)     { }
        @Override public void clearAndSelect(int index) { }
        @Override public void selectPrevious() { }
        @Override public void selectNext()     { }
        @Override public void selectFirst()    { }
        @Override public void selectLast()     { }
        @Override public void clearSelection(int index) { }
        @Override public void clearSelection() { }
        @Override public boolean isSelected(int index)   { return false; }
        @Override public boolean isEmpty()               { return true;  }
        @Override public void selectIndices(int index, int... indices) { }
        @Override public void selectAll()                 { }
        public void deselect(int index)         { }
    }
}
