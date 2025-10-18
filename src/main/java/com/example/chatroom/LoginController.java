package com.example.chatroom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtServerIP;

    @FXML
    private TextField txtPort;

    @FXML
    private Button btnLogin;

    @FXML
    private Button btnRegister;

    @FXML
    private Label lblStatus;

    @FXML
    public void initialize() {
        txtServerIP.setText("localhost");
        txtPort.setText("12345");
    }

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String serverIP = txtServerIP.getText().trim();
        String portStr = txtPort.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            Socket socket = new Socket(serverIP, port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Gửi yêu cầu đăng nhập
            writer.println("LOGIN");
            writer.println(username);
            writer.println(hashPassword(password));

            // Đợi phản hồi từ server
            String response = reader.readLine();

            if ("LOGIN_SUCCESS".equals(response)) {
                lblStatus.setText("Đăng nhập thành công!");
                lblStatus.setStyle("-fx-text-fill: green;");

                // Mở cửa sổ chat
                openChatWindow(username, socket, reader, writer);
            } else if ("LOGIN_FAILED".equals(response)) {
                lblStatus.setText("Sai tên đăng nhập hoặc mật khẩu!");
                lblStatus.setStyle("-fx-text-fill: red;");
                socket.close();
            } else if ("USER_ALREADY_ONLINE".equals(response)) {
                lblStatus.setText("Người dùng đã online!");
                lblStatus.setStyle("-fx-text-fill: orange;");
                socket.close();
            }

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Port phải là số nguyên!");
        } catch (IOException e) {
            showAlert("Lỗi kết nối", "Không thể kết nối đến server: " + e.getMessage());
        }
    }

    @FXML
    private void handleRegister() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String serverIP = txtServerIP.getText().trim();
        String portStr = txtPort.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (password.length() < 6) {
            showAlert("Lỗi", "Mật khẩu phải có ít nhất 6 ký tự!");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            Socket socket = new Socket(serverIP, port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

            // Gửi yêu cầu đăng ký
            writer.println("REGISTER");
            writer.println(username);
            writer.println(hashPassword(password));

            // Đợi phản hồi từ server
            String response = reader.readLine();

            if ("REGISTER_SUCCESS".equals(response)) {
                lblStatus.setText("Đăng ký thành công! Vui lòng đăng nhập.");
                lblStatus.setStyle("-fx-text-fill: green;");
            } else if ("REGISTER_FAILED".equals(response)) {
                lblStatus.setText("Tên đăng nhập đã tồn tại!");
                lblStatus.setStyle("-fx-text-fill: red;");
            }

            socket.close();

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Port phải là số nguyên!");
        } catch (IOException e) {
            showAlert("Lỗi kết nối", "Không thể kết nối đến server: " + e.getMessage());
        }
    }

    private void openChatWindow(String username, Socket socket, BufferedReader reader, PrintWriter writer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ChatClientView.fxml"));
            Parent root = loader.load();

            ChatClientController chatController = loader.getController();
            chatController.initializeConnection(username, socket, reader, writer);

            Stage chatStage = new Stage();
            chatStage.setTitle("Chat Room - " + username);
            chatStage.setScene(new Scene(root));
            chatStage.setOnCloseRequest(e -> {
                chatController.disconnect();
            });
            chatStage.show();

            // Đóng cửa sổ đăng nhập
            Stage loginStage = (Stage) btnLogin.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể mở cửa sổ chat!");
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return password;
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}