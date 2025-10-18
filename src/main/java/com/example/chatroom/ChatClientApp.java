package com.example.chatroom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load màn hình đăng nhập
            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            primaryStage.setTitle("VKU Chat Room - Đăng nhập");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi: Không thể load LoginView.fxml");
            System.err.println("Đảm bảo file LoginView.fxml có trong thư mục resources");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}