package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat 客户端：通过服务端分配的 USERJOIN 消息确定头像编号，保证一致性。
 */
public class ClientGUI extends Application {
    public static class ChatMessage {
        public final String sender, content;
        public ChatMessage(String s, String c) { sender = s; content = c; }
    }

    private ListView<ChatMessage> messageList;
    private TextField inputField;
    private Button sendButton;

    private String myUsername, room;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    // >>>> 头像资源列表 & 映射表
    private final List<String> avatarPaths = List.of(
        "/avatars/avatar1.png",
        "/avatars/avatar2.png",
        "/avatars/avatar3.png",
        "/avatars/avatar4.png"
    );
    private final String defaultAvatar = "/avatars/default.png";
    private final Map<String,String> avatarMap = new LinkedHashMap<>();

    @Override
    public void start(Stage stage) {
        // 1. 输入房间 & 用户名
        TextInputDialog rd = new TextInputDialog("general");
        rd.setHeaderText("Enter room name:");
        room = rd.showAndWait().orElse("general");

        TextInputDialog ud = new TextInputDialog("User");
        ud.setHeaderText("Enter your username:");
        myUsername = ud.showAndWait().orElse("User");

        // 2. 构建 UI
        messageList = new ListView<>();
        messageList.setSelectionModel(new NoSelectionModel<>());
        messageList.setCellFactory(lv -> new ChatCell());
        BorderPane.setMargin(messageList, new Insets(10));

        inputField = new TextField();
        inputField.setPromptText("Enter message…");
        inputField.setOnAction(e -> send());

        sendButton = new Button("Send");
        sendButton.setOnAction(e -> send());

        HBox bottom = new HBox(5, inputField, sendButton);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane(messageList, null, null, bottom, null);
        Scene scene = new Scene(root, 600, 400);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Room: " + room + " — " + myUsername);
        stage.setScene(scene);
        stage.show();

        // 3. 连接 & JOIN
        connectAndJoin();
    }

    private void connectAndJoin() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 9999);
                out    = new PrintWriter(socket.getOutputStream(), true);
                in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 发送 JOIN
                out.println("JOIN " + room + " " + myUsername);

                // 统一 Reader 线程
                Thread reader = new Thread(() -> {
                    try {
                        String line;
                        while ((line = in.readLine()) != null) {
                            final String msg = line;
                            System.out.println("<< " + msg);

                            // >>>> 处理服务端分配的 avatarId
                            if (msg.startsWith("USERJOIN|")) {
                                String[] p = msg.split("\\|", 3);
                                String user = p[1];
                                int avatarId = Integer.parseInt(p[2]);
                                String path = avatarId < avatarPaths.size()
                                    ? avatarPaths.get(avatarId)
                                    : defaultAvatar;
                                avatarMap.put(user, path);

                                Platform.runLater(() ->
                                    messageList.getItems().add(new ChatMessage("System", user + " joined"))
                                );
                            }
                            // 普通聊天 username|content
                            else if (msg.contains("|")) {
                                String[] p2 = msg.split("\\|", 2);
                                Platform.runLater(() ->
                                    messageList.getItems().add(new ChatMessage(p2[0], p2[1]))
                                );
                            }
                            // 其他系统消息
                            else {
                                Platform.runLater(() ->
                                    messageList.getItems().add(new ChatMessage("System", msg))
                                );
                            }
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() ->
                            messageList.getItems().add(new ChatMessage("System", "Disconnected"))
                        );
                    }
                }, "ServerReader");
                reader.setDaemon(true);
                reader.start();

            } catch (Exception e) {
                Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "Connection error: " + e.getMessage())
                        .showAndWait()
                );
            }
        }, "Connector").start();
    }

    private void send() {
        String txt = inputField.getText().trim();
        if (txt.isEmpty() || out == null) return;
        messageList.getItems().add(new ChatMessage(myUsername, txt));
        inputField.clear();
        out.println("SEND " + room + " " + txt);
    }

    @Override
    public void stop() throws Exception {
        if (out != null) {
            out.println("QUIT");
            socket.close();
        }
        super.stop();
    }

    /** 禁用 ListView 选中 */
    private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {
        @Override public ObservableList<Integer> getSelectedIndices() { return FXCollections.emptyObservableList(); }
        @Override public ObservableList<T> getSelectedItems()   { return FXCollections.emptyObservableList(); }
        @Override public void select(int i) {}
        @Override public void select(T obj) {}
        @Override public void clearAndSelect(int i) {}
        @Override public void selectPrevious() {}
        @Override public void selectNext() {}
        @Override public void selectFirst() {}
        @Override public void selectLast() {}
        @Override public void clearSelection(int i) {}
        @Override public void clearSelection() {}
        @Override public boolean isSelected(int i) { return false; }
        @Override public boolean isEmpty()         { return true; }
        @Override public void selectIndices(int index, int... indices) {}
        @Override public void selectAll() {}
        public void deselect(int index) {}
    }

    /** 渲染 ChatMessage 的单元格 */
    private class ChatCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                return;
            }

            Label lbl = new Label(msg.content);
            lbl.setWrapText(true);
            lbl.setMaxWidth(300);
            lbl.getStyleClass().add(
                msg.sender.equals(myUsername) ? "my-bubble" : "other-bubble"
            );

            // >>>> 安全加载头像
            String avatarPath = avatarMap.getOrDefault(msg.sender, defaultAvatar);
            URL url = getClass().getResource(avatarPath);
            ImageView avatar;
            if (url != null) {
                avatar = new ImageView(new Image(url.toExternalForm(), 32,32,true,true));
            } else {
                avatar = new ImageView();
                avatar.setFitWidth(32);
                avatar.setFitHeight(32);
            }

            HBox box = new HBox(8);
            if (msg.sender.equals(myUsername)) {
                box.setAlignment(Pos.TOP_RIGHT);
                box.getChildren().setAll(lbl, avatar);
            } else {
                box.setAlignment(Pos.TOP_LEFT);
                box.getChildren().setAll(avatar, lbl);
            }
            setGraphic(box);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
