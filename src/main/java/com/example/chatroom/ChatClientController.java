package com.example.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import java.io.*;
import java.net.Socket;

public class ChatClientController {

    @FXML
    private TextArea txtChatArea;

    @FXML
    private TextField txtMessage;

    @FXML
    private Button btnSend;

    @FXML
    private ListView<String> listOnlineUsers;

    @FXML
    private Label lblCurrentChat;

    @FXML
    private Label lblUsername;

    @FXML
    private Button btnDisconnect;

    @FXML
    private RadioButton rbPublic;

    @FXML
    private RadioButton rbPrivate;

    @FXML
    private RadioButton rbGroup;

    @FXML
    private TextField txtGroupMembers;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private boolean isConnected = false;
    private String chatMode = "PUBLIC"; // PUBLIC, PRIVATE, GROUP
    private String selectedUser = null;

    @FXML
    private ListView<String> listGroupMembers;


    @FXML
    public void initialize() {
        txtMessage.setDisable(true);
        btnSend.setDisable(true);
        txtChatArea.setEditable(false);
//        txtGroupMembers.setDisable(true);
        listGroupMembers.setDisable(true);

        txtMessage.setOnKeyPressed(this::handleMessageKeyPress);

        ToggleGroup group = new ToggleGroup();
        rbPublic.setToggleGroup(group);
        rbPrivate.setToggleGroup(group);
        rbGroup.setToggleGroup(group);
        rbPublic.setSelected(true);

        listGroupMembers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listGroupMembers.setDisable(true); // mặc định tắt khi chưa chọn Group mode


        listOnlineUsers.setOnMouseClicked(this::handleUserListClick);
    }

    public void initializeConnection(String username, Socket socket, BufferedReader reader, PrintWriter writer) {
        this.username = username;
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.isConnected = true;

        lblUsername.setText("User: " + username);
        lblCurrentChat.setText("Chế độ: Chat công khai");

        txtMessage.setDisable(false);
        btnSend.setDisable(false);

        appendMessage("✓ Đã kết nối đến server thành công!");
        appendMessage("✓ Chào mừng " + username + " đến với phòng chat!");

        // Bắt đầu nhận tin nhắn
        Thread receiveThread = new Thread(this::receiveMessages);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    @FXML
    private void handlePublicMode() {
        chatMode = "PUBLIC";
        selectedUser = null;
        lblCurrentChat.setText("Chế độ: Chat công khai (tất cả)");
//        txtGroupMembers.setDisable(true);
        listGroupMembers.setDisable(true);
        txtChatArea.clear();
        listGroupMembers.setDisable(true);
        listGroupMembers.getSelectionModel().clearSelection();

        appendMessage(">>> Đã chuyển sang chế độ chat công khai");
    }

    @FXML
    private void handlePrivateMode() {
        chatMode = "PRIVATE";
//        txtGroupMembers.setDisable(true);
        listGroupMembers.setDisable(true);
        listGroupMembers.getSelectionModel().clearSelection();

        if (selectedUser != null) {
            lblCurrentChat.setText("Chế độ: Chat riêng với " + selectedUser);
            txtChatArea.clear();
            appendMessage(">>> Đã chuyển sang chat riêng với " + selectedUser);
        } else {
            lblCurrentChat.setText("Chế độ: Chat riêng (chọn người dùng)");
            appendMessage(">>> Vui lòng chọn người dùng để chat riêng");
        }
    }

    @FXML
    private void handleGroupMode() {
        chatMode = "GROUP";
        selectedUser = null;
//        txtGroupMembers.setDisable(false);
        listGroupMembers.setDisable(false);
        listGroupMembers.getSelectionModel().clearSelection();

        lblCurrentChat.setText("Chế độ: Chat nhóm");
        txtChatArea.clear();
        appendMessage(">>> Đã chuyển sang chế độ chat nhóm");
        appendMessage(">>> Tương tác bằng cách giữ phim CTRL + click chuột để chọn người nhận");
    }

    private void handleUserListClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            String selected = listOnlineUsers.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals(username)) {
                selectedUser = selected;
                rbPrivate.setSelected(true);
                handlePrivateMode();
            }
        } else if (event.getClickCount() == 1) {
            String selected = listOnlineUsers.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals(username)) {
                selectedUser = selected;
            }
        }
    }

    @FXML
    private void handleDisconnect() {
        disconnect();
        System.exit(0);
    }

    public void disconnect() {
        if (isConnected) {
            try {
                if (writer != null) {
                    writer.println("/quit");
                }
                if (socket != null) {
                    socket.close();
                }
                isConnected = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleSend() {
        sendMessage();
    }

    private void handleMessageKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            sendMessage();
        }
    }

    private void sendMessage() {
        String message = txtMessage.getText().trim();
        if (message.isEmpty() || !isConnected) {
            return;
        }

        try {
            switch (chatMode) {
                case "PUBLIC":
                    writer.println("PUBLIC:" + message);
                    break;

                case "PRIVATE":
                    if (selectedUser != null) {
                        writer.println("PRIVATE:" + selectedUser + ":" + message);
                        appendMessage("[Bạn -> " + selectedUser + " (riêng)]: " + message);
                    } else {
                        appendMessage(">>> Lỗi: Vui lòng chọn người dùng để chat riêng!");
                    }
                    break;

//                case "GROUP":
//                    String members = txtGroupMembers.getText().trim();
//                    if (!members.isEmpty()) {
//                        writer.println("GROUP:" + members + ":" + message);
//                        appendMessage("[Bạn -> Nhóm (" + members + ")]: " + message);
//                    } else {
//                        appendMessage(">>> Lỗi: Vui lòng nhập danh sách thành viên nhóm!");
//                    }
//                    break;
                case "GROUP":
                    var selectedMembers = listGroupMembers.getSelectionModel().getSelectedItems();
                    if (selectedMembers.isEmpty()) {
                        appendMessage(">>> Lỗi: Vui lòng chọn ít nhất một thành viên trong nhóm!");
                        break;
                    }

                    String members = String.join(",", selectedMembers);
                    writer.println("GROUP:" + members + ":" + message);
                    appendMessage("[Bạn -> Nhóm (" + members + ")]: " + message);
                    break;

            }

            txtMessage.clear();
        } catch (Exception e) {
            appendMessage(">>> Lỗi khi gửi tin nhắn: " + e.getMessage());
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                String finalMessage = message;

                if (message.startsWith("USERS:")) {
                    String[] users = message.substring(6).split(",");
                    Platform.runLater(() -> updateUserList(users));
                } else {
                    Platform.runLater(() -> appendMessage(finalMessage));
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                Platform.runLater(() -> {
                    appendMessage(">>> Mất kết nối với server!");
                    disconnect();
                });
            }
        }
    }

    private void updateUserList(String[] users) {
        listOnlineUsers.getItems().clear();
        listGroupMembers.getItems().clear();

        for (String user : users) {
            String trimmed = user.trim();
            if (!trimmed.isEmpty()) {
                listOnlineUsers.getItems().add(trimmed);
                if (!trimmed.equals(username)) { // không tự chọn chính mình
                    listGroupMembers.getItems().add(trimmed);
                }
            }
        }
    }


//    private void updateUserList(String[] users) {
//        listOnlineUsers.getItems().clear();
//        for (String user : users) {
//            if (!user.trim().isEmpty()) {
//                listOnlineUsers.getItems().add(user.trim());
//            }
//        }
//    }

    private void appendMessage(String message) {
        txtChatArea.appendText(message + "\n");
    }


}

