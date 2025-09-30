module com.example.chatroom {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.chatroom to javafx.fxml;
    exports com.example.chatroom;
}