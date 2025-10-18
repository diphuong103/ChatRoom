package com.example.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class ServerGUIController {

    @FXML
    private TextArea txtServerLog;

    @FXML
    private TextField txtPort;

    @FXML
    private Button btnStart;

    @FXML
    private Button btnStop;

    @FXML
    private Label lblStatus;

    @FXML
    private Label lblOnlineUsers;

    @FXML
    private Label lblTotalConnections;

    @FXML
    private ListView<String> listConnectedUsers;

    private Thread serverThread;
    private boolean isRunning = false;

    @FXML
    public void initialize() {
        txtPort.setText("12345");
        btnStop.setDisable(true);
        lblStatus.setText("Server đã dừng");
        lblStatus.setTextFill(Color.RED);

        // Redirect System.out to TextArea
        redirectSystemOut();

        // Update user list periodically
        Thread updateThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(this::updateUserList);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    @FXML
    private void handleStart() {
        String portStr = txtPort.getText().trim();

        try {
            int port = Integer.parseInt(portStr);

            serverThread = new Thread(() -> {
                ChatServerGUI.startServer(port);
            });
            serverThread.setDaemon(true);
            serverThread.start();

            isRunning = true;
            btnStart.setDisable(true);
            btnStop.setDisable(false);
            txtPort.setDisable(true);

            lblStatus.setText("Server đang chạy");
            lblStatus.setTextFill(Color.GREEN);

            appendLog("✓ Server đã khởi động thành công trên port " + port);

        } catch (NumberFormatException e) {
            showAlert("Lỗi", "Port phải là số nguyên!");
        }
    }

    @FXML
    private void handleStop() {
        if (isRunning) {
            ChatServerGUI.stopServer();
            isRunning = false;

            btnStart.setDisable(false);
            btnStop.setDisable(true);
            txtPort.setDisable(false);

            lblStatus.setText("Server đã dừng");
            lblStatus.setTextFill(Color.RED);

            appendLog("✓ Server đã dừng");
        }
    }

    @FXML
    private void handleClearLog() {
        txtServerLog.clear();
    }

    private void updateUserList() {
        listConnectedUsers.getItems().clear();
        int count = 0;
        for (String username : ChatServerGUI.getOnlineUsers()) {
            listConnectedUsers.getItems().add(username);
            count++;
        }
        lblOnlineUsers.setText("Online: " + count);
        lblTotalConnections.setText("Tổng kết nối: " + ChatServerGUI.getTotalConnections());
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            txtServerLog.appendText(message + "\n");
        });
    }

    private void redirectSystemOut() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                Platform.runLater(() -> {
                    txtServerLog.appendText(String.valueOf((char) b));
                });
            }
        };
        System.setOut(new PrintStream(out, true));
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}