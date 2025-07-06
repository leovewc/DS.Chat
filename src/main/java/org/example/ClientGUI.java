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

/**
 * 完整的 QQ-风格群聊客户端 UI，实现左右消息气泡＋头像＋用户名显示。
 */
public class ClientGUI extends Application {
    // 用于在 ListView 中展示的消息模型
    public static class ChatMessage {
        public final String sender, content;
        public ChatMessage(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }
    }

    private ListView<ChatMessage> messageList;
    private TextField inputField;
    private Button sendButton;

    private String myUsername;
    private String room;

    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    @Override
    public void start(Stage stage) {
        // —— 1. 弹窗输入房间与用户名 ——
        TextInputDialog rd = new TextInputDialog("general");
        rd.setHeaderText("输入房间名：");
        room = rd.showAndWait().orElse("general");

        TextInputDialog ud = new TextInputDialog("User");
        ud.setHeaderText("输入用户名：");
        myUsername = ud.showAndWait().orElse("User");

        // —— 2. 构建 UI ——
        messageList = new ListView<>();
        messageList.setSelectionModel(new NoSelectionModel<>());
        messageList.setCellFactory(lv -> new ChatCell(myUsername));
        BorderPane.setMargin(messageList, new Insets(10));

        inputField = new TextField();
        inputField.setPromptText("请输入消息…");
        inputField.setOnAction(e -> send());

        sendButton = new Button("Send");
        sendButton.setOnAction(e -> send());

        HBox bottom = new HBox(5, inputField, sendButton);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane(messageList, null, null, bottom, null);
        Scene scene = new Scene(root, 600, 400);
        // 请确保 styles.css 在 resources 目录下
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Chat Room: " + room + " — " + myUsername);
        stage.setScene(scene);
        stage.show();

        // —— 3. 建立连接并 JOIN ——
        connectAndJoin();
    }

    private void connectAndJoin() {
        new Thread(() -> {
            try {
                socket = new Socket("localhost", 9999);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 发送 JOIN 命令
                out.println("JOIN " + room + " " + myUsername);

                // 启动消息监听线程
                Thread reader = new Thread(() -> {
                    try {
                        String line;
                        while ((line = in.readLine()) != null) {
                            // 只处理“username|message”格式
                            if (line.contains("|")) {
                                String[] p = line.split("\\|", 2);
                                String sender = p[0], content = p[1];
                                Platform.runLater(() ->
                                    messageList.getItems().add(new ChatMessage(sender, content))
                                );
                            }
                        }
                    } catch (Exception e) {
                        Platform.runLater(() ->
                            messageList.getItems().add(new ChatMessage("System", "已断开与服务器连接"))
                        );
                    }
                }, "ServerReader");
                reader.setDaemon(true);
                reader.start();

            } catch (Exception ex) {
                Platform.runLater(() ->
                    new Alert(Alert.AlertType.ERROR, "连接失败: " + ex.getMessage()).showAndWait()
                );
            }
        }, "Connector").start();
    }

    private void send() {
        String txt = inputField.getText().trim();
        if (txt.isEmpty() || out == null) return;
        // 本地先显示一条
        messageList.getItems().add(new ChatMessage(myUsername, txt));
        inputField.clear();
        // 再发给服务器
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

    /** 禁用 ListView 的选中 */
    private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {
        @Override public ObservableList<Integer> getSelectedIndices() { return FXCollections.emptyObservableList(); }
        @Override public ObservableList<T> getSelectedItems() { return FXCollections.emptyObservableList(); }
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
        @Override public boolean isEmpty() { return true; }
        @Override public void selectIndices(int index, int... indices) {}
        @Override public void selectAll() {}
        public void deselect(int index) {}
    }

    /** 自定义 Cell，根据 sender 决定左右和样式 */
    private static class ChatCell extends ListCell<ChatMessage> {
        private final String me;
        public ChatCell(String me) { this.me = me; }

        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) { setGraphic(null); return; }

            Label lbl = new Label(msg.content);
            lbl.setWrapText(true);
            lbl.setMaxWidth(300);
            lbl.getStyleClass().add(msg.sender.equals(me) ? "my-bubble" : "other-bubble");

            ImageView avatar = new ImageView(new Image(
                msg.sender.equals(me) ? "/avatars/me.png" : "/avatars/other.png",
                32, 32, true, true
            ));

            HBox box = new HBox(8);
            if (msg.sender.equals(me)) {
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
