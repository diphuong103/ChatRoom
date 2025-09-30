package com.example.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.io.*;
import java.net.Socket;

public class ChatClientController {

    @FXML
    private TextField txtServerIP;

    @FXML
    private TextField txtPort;

    @FXML
    private TextField txtUsername;

    @FXML
    private Button btnConnect;

    @FXML
    private Button btnDisconnect;

    @FXML
    private TextArea txtChatArea;

    @FXML
    private TextField txtMessage;

    @FXML
    private Button btnSend;

    @FXML
    private ListView<String> listOnlineUsers;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean isConnected = false;

    @FXML
    public void initialize() {
        // Thiết lập trạng thái ban đầu
        txtServerIP.setText("localhost");
        txtPort.setText("12345");
        btnDisconnect.setDisable(true);
        txtMessage.setDisable(true);
        btnSend.setDisable(true);
        txtChatArea.setEditable(false);

        // Xử lý sự kiện nhấn Enter trong txtMessage
        txtMessage.setOnKeyPressed(this::handleMessageKeyPress);
    }

    @FXML
    private void handleConnect() {
        String serverIP = txtServerIP.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();

        if (username.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập tên người dùng!");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            socket = new Socket(serverIP, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Gửi username đến server
            writer.println(username);

            isConnected = true;
            updateUIAfterConnect();
            appendMessage("Đã kết nối đến server!");

            // Tạo thread để nhận tin nhắn từ server
            Thread receiveThread = new Thread(this::receiveMessages);
            receiveThread.setDaemon(true);
            receiveThread.start();

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Port phải là số nguyên!");
        } catch (IOException e) {
            showAlert("Lỗi kết nối", "Không thể kết nối đến server: " + e.getMessage());
        }
    }

    @FXML
    private void handleDisconnect() {
        disconnect();
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
                Platform.runLater(() -> {
                    updateUIAfterDisconnect();
                    appendMessage("Đã ngắt kết nối khỏi server!");
                });
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
        if (!message.isEmpty() && isConnected) {
            writer.println(message);
            txtMessage.clear();
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                String finalMessage = message;

                // Xử lý các loại tin nhắn đặc biệt
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
                    appendMessage("Mất kết nối với server!");
                    disconnect();
                });
            }
        }
    }

    private void updateUserList(String[] users) {
        listOnlineUsers.getItems().clear();
        for (String user : users) {
            if (!user.trim().isEmpty()) {
                listOnlineUsers.getItems().add(user.trim());
            }
        }
    }

    private void appendMessage(String message) {
        txtChatArea.appendText(message + "\n");
    }

    private void updateUIAfterConnect() {
        txtServerIP.setDisable(true);
        txtPort.setDisable(true);
        txtUsername.setDisable(true);
        btnConnect.setDisable(true);
        btnDisconnect.setDisable(false);
        txtMessage.setDisable(false);
        btnSend.setDisable(false);
    }

    private void updateUIAfterDisconnect() {
        txtServerIP.setDisable(false);
        txtPort.setDisable(false);
        txtUsername.setDisable(false);
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        txtMessage.setDisable(true);
        btnSend.setDisable(true);
        listOnlineUsers.getItems().clear();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}