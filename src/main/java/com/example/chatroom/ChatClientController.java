package com.example.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import java.io.*;
import java.net.Socket;
import java.util.*;

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
    private ListView<String> listGroupMembers;

    @FXML
    private Button btnCreateGroup;

    @FXML
    private Button btnManageGroups;

    @FXML
    private ListView<String> listMyGroups;

    @FXML
    private Label lblGroupInfo;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private boolean isConnected = false;
    private String chatMode = "PUBLIC";
    private String selectedUser = null;
    private String currentGroup = null;

    // Lưu trữ thông tin các nhóm: tên nhóm -> danh sách thành viên
    private Map<String, List<String>> myGroups = new HashMap<>();

    @FXML
    public void initialize() {
        txtMessage.setDisable(true);
        btnSend.setDisable(true);
        txtChatArea.setEditable(false);
        listGroupMembers.setDisable(true);

        txtMessage.setOnKeyPressed(this::handleMessageKeyPress);

        ToggleGroup group = new ToggleGroup();
        rbPublic.setToggleGroup(group);
        rbPrivate.setToggleGroup(group);
        rbGroup.setToggleGroup(group);
        rbPublic.setSelected(true);

        listGroupMembers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listOnlineUsers.setOnMouseClicked(this::handleUserListClick);
        listMyGroups.setOnMouseClicked(this::handleGroupListClick);
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

        Thread receiveThread = new Thread(this::receiveMessages);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    @FXML
    private void handlePublicMode() {
        chatMode = "PUBLIC";
        selectedUser = null;
        currentGroup = null;
        lblCurrentChat.setText("Chế độ: Chat công khai (tất cả)");
        listGroupMembers.setDisable(true);
        listGroupMembers.getSelectionModel().clearSelection();
        txtChatArea.clear();
        appendMessage(">>> Đã chuyển sang chế độ chat công khai");
    }

    @FXML
    private void handlePrivateMode() {
        chatMode = "PRIVATE";
        currentGroup = null;
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
        listGroupMembers.setDisable(false);
        listGroupMembers.getSelectionModel().clearSelection();

        if (currentGroup != null) {
            lblCurrentChat.setText("Chế độ: Chat nhóm - " + currentGroup);
        } else {
            lblCurrentChat.setText("Chế độ: Chat nhóm (chọn hoặc tạo nhóm)");
        }

        txtChatArea.clear();
        appendMessage(">>> Đã chuyển sang chế độ chat nhóm");
        appendMessage(">>> Giữ phím CTRL + click để chọn nhiều người hoặc chọn nhóm có sẵn");
    }

    @FXML
    private void handleCreateGroup() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tạo nhóm chat mới");
        dialog.setHeaderText("Nhập thông tin nhóm");

        ButtonType createButtonType = new ButtonType("Tạo nhóm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField txtGroupName = new TextField();
        txtGroupName.setPromptText("Tên nhóm");

        ListView<String> memberList = new ListView<>();
        memberList.setPrefHeight(200);
        memberList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Thêm danh sách users online (trừ bản thân)
        for (String user : listOnlineUsers.getItems()) {
            if (!user.equals(username)) {
                memberList.getItems().add(user);
            }
        }

        grid.add(new Label("Tên nhóm:"), 0, 0);
        grid.add(txtGroupName, 1, 0);
        grid.add(new Label("Chọn thành viên:"), 0, 1);
        grid.add(memberList, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> txtGroupName.requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == createButtonType) {
            String groupName = txtGroupName.getText().trim();
            List<String> selectedMembers = new ArrayList<>(memberList.getSelectionModel().getSelectedItems());

            if (groupName.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập tên nhóm!");
                return;
            }

            if (selectedMembers.isEmpty()) {
                showAlert("Lỗi", "Vui lòng chọn ít nhất một thành viên!");
                return;
            }

            if (myGroups.containsKey(groupName)) {
                showAlert("Lỗi", "Tên nhóm đã tồn tại!");
                return;
            }

            // Tạo nhóm mới
            myGroups.put(groupName, selectedMembers);
            updateGroupList();

            currentGroup = groupName;
            rbGroup.setSelected(true);
            handleGroupMode();

            appendMessage(">>> Đã tạo nhóm '" + groupName + "' với " + selectedMembers.size() + " thành viên");
            showInfo("Thành công", "Nhóm '" + groupName + "' đã được tạo!");
        }
    }

    @FXML
    private void handleManageGroups() {
        if (myGroups.isEmpty()) {
            showAlert("Thông báo", "Bạn chưa có nhóm nào. Hãy tạo nhóm mới!");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Quản lý nhóm chat");
        dialog.setHeaderText("Chọn nhóm để quản lý");

        ButtonType closeButtonType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButtonType);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ListView<String> groupListView = new ListView<>();
        groupListView.getItems().addAll(myGroups.keySet());
        groupListView.setPrefHeight(150);

        ListView<String> memberListView = new ListView<>();
        memberListView.setPrefHeight(150);

        Button btnAddMember = new Button("Thêm thành viên");
        Button btnRemoveMember = new Button("Xóa thành viên");
        Button btnDeleteGroup = new Button("Xóa nhóm");

        btnAddMember.setDisable(true);
        btnRemoveMember.setDisable(true);
        btnDeleteGroup.setDisable(true);

        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                memberListView.getItems().clear();
                memberListView.getItems().addAll(myGroups.get(newVal));
                btnAddMember.setDisable(false);
                btnDeleteGroup.setDisable(false);
            }
        });

        memberListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnRemoveMember.setDisable(newVal == null);
        });

        btnAddMember.setOnAction(e -> {
            String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
            if (selectedGroup != null) {
                addMemberToGroup(selectedGroup, memberListView);
            }
        });

        btnRemoveMember.setOnAction(e -> {
            String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
            String selectedMember = memberListView.getSelectionModel().getSelectedItem();
            if (selectedGroup != null && selectedMember != null) {
                myGroups.get(selectedGroup).remove(selectedMember);
                memberListView.getItems().remove(selectedMember);
                appendMessage(">>> Đã xóa " + selectedMember + " khỏi nhóm " + selectedGroup);
            }
        });

        btnDeleteGroup.setOnAction(e -> {
            String selectedGroup = groupListView.getSelectionModel().getSelectedItem();
            if (selectedGroup != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Xác nhận");
                confirm.setHeaderText("Xóa nhóm " + selectedGroup + "?");
                confirm.setContentText("Bạn có chắc chắn muốn xóa nhóm này?");

                Optional<ButtonType> confirmResult = confirm.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    myGroups.remove(selectedGroup);
                    groupListView.getItems().remove(selectedGroup);
                    memberListView.getItems().clear();
                    updateGroupList();

                    if (selectedGroup.equals(currentGroup)) {
                        currentGroup = null;
                        lblCurrentChat.setText("Chế độ: Chat nhóm (chọn hoặc tạo nhóm)");
                    }

                    appendMessage(">>> Đã xóa nhóm " + selectedGroup);
                }
            }
        });

        grid.add(new Label("Danh sách nhóm:"), 0, 0);
        grid.add(groupListView, 0, 1);
        grid.add(new Label("Thành viên:"), 1, 0);
        grid.add(memberListView, 1, 1);

        GridPane buttonPane = new GridPane();
        buttonPane.setHgap(5);
        buttonPane.add(btnAddMember, 0, 0);
        buttonPane.add(btnRemoveMember, 1, 0);
        buttonPane.add(btnDeleteGroup, 2, 0);
        grid.add(buttonPane, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    private void addMemberToGroup(String groupName, ListView<String> memberListView) {
        List<String> availableUsers = new ArrayList<>();
        for (String user : listOnlineUsers.getItems()) {
            if (!user.equals(username) && !myGroups.get(groupName).contains(user)) {
                availableUsers.add(user);
            }
        }

        if (availableUsers.isEmpty()) {
            showAlert("Thông báo", "Không có người dùng nào để thêm vào nhóm!");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(availableUsers.get(0), availableUsers);
        dialog.setTitle("Thêm thành viên");
        dialog.setHeaderText("Chọn người dùng để thêm vào nhóm " + groupName);
        dialog.setContentText("Người dùng:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(user -> {
            myGroups.get(groupName).add(user);
            memberListView.getItems().add(user);
            appendMessage(">>> Đã thêm " + user + " vào nhóm " + groupName);
        });
    }

    private void handleGroupListClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            String selected = listMyGroups.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentGroup = selected;
                rbGroup.setSelected(true);
                handleGroupMode();

                List<String> members = myGroups.get(currentGroup);
                lblGroupInfo.setText("Nhóm: " + currentGroup + " (" + members.size() + " thành viên)");
                appendMessage(">>> Đã chọn nhóm: " + currentGroup);
                appendMessage(">>> Thành viên: " + String.join(", ", members));
            }
        }
    }

    private void updateGroupList() {
        listMyGroups.getItems().clear();
        listMyGroups.getItems().addAll(myGroups.keySet());
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

                case "GROUP":
                    String members;
                    if (currentGroup != null && myGroups.containsKey(currentGroup)) {
                        // Sử dụng nhóm đã lưu
                        members = String.join(",", myGroups.get(currentGroup));
                        writer.println("GROUP:" + members + ":" + message);
                        appendMessage("[Bạn -> Nhóm " + currentGroup + "]: " + message);
                    } else {
                        // Chọn thành viên tạm thời
                        var selectedMembers = listGroupMembers.getSelectionModel().getSelectedItems();
                        if (selectedMembers.isEmpty()) {
                            appendMessage(">>> Lỗi: Vui lòng chọn nhóm hoặc chọn thành viên!");
                            break;
                        }
                        members = String.join(",", selectedMembers);
                        writer.println("GROUP:" + members + ":" + message);
                        appendMessage("[Bạn -> Nhóm (" + members + ")]: " + message);
                    }
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
                if (!trimmed.equals(username)) {
                    listGroupMembers.getItems().add(trimmed);
                }
            }
        }
    }

    private void appendMessage(String message) {
        txtChatArea.appendText(message + "\n");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}