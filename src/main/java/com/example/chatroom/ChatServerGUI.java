package com.example.chatroom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServerGUI extends Application {

    private static ServerSocket serverSocket;
    private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private static boolean isRunning = false;

    // Lưu trữ nhóm: tên nhóm -> GroupInfo
    private static Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("ServerGUIView.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("Chat Server Manager");
        primaryStage.setScene(new Scene(root));
        primaryStage.setOnCloseRequest(e -> {
            stopServer();
            System.exit(0);
        });
        primaryStage.show();
    }

    public static void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("✓ Server đang chạy trên port " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    new Thread(clientHandler).start();
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Lỗi kết nối client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi khởi động server: " + e.getMessage());
        }
    }

    public static void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.disconnect();
                }
                clients.clear();
            }
            onlineUsers.clear();
            groups.clear();
        } catch (IOException e) {
            System.err.println("Lỗi khi đóng server: " + e.getMessage());
        }
    }

    public static List<String> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.keySet());
    }

    public static int getTotalConnections() {
        return clients.size();
    }

    private static void broadcastMessage(String message, ClientHandler excludeClient) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != excludeClient && client.isLoggedIn()) {
                    client.sendMessage(message);
                }
            }
        }
    }

    private static void broadcastUserList() {
        String userList = "USERS:" + String.join(",", onlineUsers.keySet());
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.isLoggedIn()) {
                    client.sendMessage(userList);
                }
            }
        }
    }

    private static void broadcastGroupList(String username) {
        // Gửi danh sách nhóm mà user này là thành viên
        StringBuilder groupList = new StringBuilder("GROUPS:");

        for (Map.Entry<String, GroupInfo> entry : groups.entrySet()) {
            GroupInfo group = entry.getValue();
            if (group.isMember(username)) {
                groupList.append(entry.getKey()).append(":").append(group.getMembersString()).append(";");
            }
        }

        ClientHandler client = onlineUsers.get(username);
        if (client != null) {
            client.sendMessage(groupList.toString());
        }
    }

    private static void broadcastGroupUpdate(String groupName) {
        GroupInfo group = groups.get(groupName);
        if (group == null) return;

        String groupUpdate = "GROUP_UPDATE:" + groupName + ":" + group.getMembersString();

        // Gửi đến tất cả thành viên trong nhóm
        for (String member : group.getMembers()) {
            ClientHandler client = onlineUsers.get(member);
            if (client != null) {
                client.sendMessage(groupUpdate);
            }
        }
    }

    // Class lưu thông tin nhóm
    static class GroupInfo {
        private String creator;
        private Set<String> members;
        private long createdTime;

        public GroupInfo(String creator, Set<String> members) {
            this.creator = creator;
            this.members = new HashSet<>(members);
            this.createdTime = System.currentTimeMillis();
        }

        public boolean isMember(String username) {
            return members.contains(username) || creator.equals(username);
        }

        public Set<String> getMembers() {
            return new HashSet<>(members);
        }

        public String getMembersString() {
            return String.join(",", members);
        }

        public void addMember(String username) {
            members.add(username);
        }

        public void removeMember(String username) {
            members.remove(username);
        }

        public String getCreator() {
            return creator;
        }
    }

    // ClientHandler class
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String username;
        private boolean isLoggedIn = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);
                clients.add(this);
            } catch (IOException e) {
                System.err.println("Lỗi khởi tạo ClientHandler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                handleAuthentication();

                if (isLoggedIn) {
                    String message;
                    while ((message = reader.readLine()) != null) {
                        if (message.equals("/quit")) {
                            break;
                        }
                        handleMessage(message);
                    }
                }
            } catch (IOException e) {
                System.out.println("Client " + username + " ngắt kết nối");
            } finally {
                disconnect();
            }
        }

        private void handleAuthentication() throws IOException {
            String action = reader.readLine();
            String username = reader.readLine();
            String password = reader.readLine();

            if ("REGISTER".equals(action)) {
                handleRegister(username, password);
            } else if ("LOGIN".equals(action)) {
                handleLogin(username, password);
            }
        }

        private void handleRegister(String username, String password) {
            File userFile = new File("users/" + username + ".txt");

            if (userFile.exists()) {
                writer.println("REGISTER_FAILED");
                System.out.println("Đăng ký thất bại: " + username + " (đã tồn tại)");
            } else {
                try {
                    new File("users").mkdirs();
                    FileWriter fw = new FileWriter(userFile);
                    fw.write(password);
                    fw.close();
                    writer.println("REGISTER_SUCCESS");
                    System.out.println("✓ Đăng ký thành công: " + username);
                } catch (IOException e) {
                    writer.println("REGISTER_FAILED");
                    System.err.println("Lỗi đăng ký: " + e.getMessage());
                }
            }
        }

        private void handleLogin(String username, String password) {
            if (onlineUsers.containsKey(username)) {
                writer.println("USER_ALREADY_ONLINE");
                System.out.println("Đăng nhập thất bại: " + username + " (đã online)");
                return;
            }

            File userFile = new File("users/" + username + ".txt");

            if (!userFile.exists()) {
                writer.println("LOGIN_FAILED");
                System.out.println("Đăng nhập thất bại: " + username + " (không tồn tại)");
                return;
            }

            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(userFile));
                String storedPassword = fileReader.readLine();
                fileReader.close();

                if (password.equals(storedPassword)) {
                    this.username = username;
                    this.isLoggedIn = true;
                    onlineUsers.put(username, this);

                    writer.println("LOGIN_SUCCESS");
                    System.out.println("✓ " + username + " đã đăng nhập");

                    broadcastMessage(">>> " + username + " đã tham gia phòng chat!", this);
                    broadcastUserList();

                    // Gửi danh sách nhóm cho user vừa đăng nhập
                    broadcastGroupList(username);
                } else {
                    writer.println("LOGIN_FAILED");
                    System.out.println("Đăng nhập thất bại: " + username + " (sai mật khẩu)");
                }
            } catch (IOException e) {
                writer.println("LOGIN_FAILED");
                System.err.println("Lỗi đọc file: " + e.getMessage());
            }
        }

        private void handleMessage(String message) {
            String[] parts = message.split(":", 3);

            if (parts.length < 2) {
                return;
            }

            String messageType = parts[0];

            switch (messageType) {
                case "PUBLIC":
                    if (parts.length >= 2) {
                        String content = parts[1];
                        broadcastMessage("[" + username + "]: " + content, null);
                        System.out.println("PUBLIC [" + username + "]: " + content);
                    }
                    break;

                case "PRIVATE":
                    if (parts.length >= 3) {
                        String targetUser = parts[1];
                        String content = parts[2];
                        sendPrivateMessage(targetUser, content);
                    }
                    break;

                case "GROUP":
                    if (parts.length >= 3) {
                        String members = parts[1];
                        String content = parts[2];
                        sendGroupMessage(members, content);
                    }
                    break;

                case "CREATE_GROUP":
                    if (parts.length >= 3) {
                        String groupName = parts[1];
                        String members = parts[2];
                        handleCreateGroup(groupName, members);
                    }
                    break;

                case "DELETE_GROUP":
                    if (parts.length >= 2) {
                        String groupName = parts[1];
                        handleDeleteGroup(groupName);
                    }
                    break;

                case "ADD_MEMBER":
                    if (parts.length >= 3) {
                        String groupName = parts[1];
                        String newMember = parts[2];
                        handleAddMember(groupName, newMember);
                    }
                    break;

                case "REMOVE_MEMBER":
                    if (parts.length >= 3) {
                        String groupName = parts[1];
                        String memberToRemove = parts[2];
                        handleRemoveMember(groupName, memberToRemove);
                    }
                    break;
            }
        }

        private void handleCreateGroup(String groupName, String membersStr) {
            if (groups.containsKey(groupName)) {
                writer.println("GROUP_ERROR:Tên nhóm đã tồn tại");
                return;
            }

            String[] memberArray = membersStr.split(",");
            Set<String> members = new HashSet<>();

            for (String member : memberArray) {
                String trimmed = member.trim();
                if (!trimmed.isEmpty() && !trimmed.equals(username)) {
                    members.add(trimmed);
                }
            }

            if (members.isEmpty()) {
                writer.println("GROUP_ERROR:Nhóm phải có ít nhất 1 thành viên");
                return;
            }

            // Tạo nhóm mới
            GroupInfo group = new GroupInfo(username, members);
            groups.put(groupName, group);

            writer.println("GROUP_CREATED:" + groupName + ":" + membersStr);
            System.out.println("✓ Nhóm '" + groupName + "' được tạo bởi " + username);

            // Thông báo cho tất cả thành viên
            String notification = "GROUP_ADDED:" + groupName + ":" + membersStr + ":" + username;
            for (String member : members) {
                ClientHandler client = onlineUsers.get(member);
                if (client != null) {
                    client.sendMessage(notification);
                }
            }

            broadcastMessage(">>> " + username + " đã tạo nhóm '" + groupName + "'", null);
        }

        private void handleDeleteGroup(String groupName) {
            GroupInfo group = groups.get(groupName);

            if (group == null) {
                writer.println("GROUP_ERROR:Nhóm không tồn tại");
                return;
            }

            if (!group.getCreator().equals(username)) {
                writer.println("GROUP_ERROR:Chỉ người tạo mới có quyền xóa nhóm");
                return;
            }

            // Thông báo cho tất cả thành viên
            String notification = "GROUP_DELETED:" + groupName;
            for (String member : group.getMembers()) {
                ClientHandler client = onlineUsers.get(member);
                if (client != null) {
                    client.sendMessage(notification);
                }
            }

            groups.remove(groupName);
            writer.println("GROUP_DELETED:" + groupName);
            System.out.println("✓ Nhóm '" + groupName + "' đã bị xóa bởi " + username);

            broadcastMessage(">>> " + username + " đã xóa nhóm '" + groupName + "'", null);
        }

        private void handleAddMember(String groupName, String newMember) {
            GroupInfo group = groups.get(groupName);

            if (group == null) {
                writer.println("GROUP_ERROR:Nhóm không tồn tại");
                return;
            }

            if (!group.getCreator().equals(username)) {
                writer.println("GROUP_ERROR:Chỉ người tạo mới có quyền thêm thành viên");
                return;
            }

            if (group.isMember(newMember)) {
                writer.println("GROUP_ERROR:Thành viên đã có trong nhóm");
                return;
            }

            group.addMember(newMember);

            // Thông báo cho thành viên mới
            ClientHandler newClient = onlineUsers.get(newMember);
            if (newClient != null) {
                String notification = "GROUP_ADDED:" + groupName + ":" + group.getMembersString() + ":" + username;
                newClient.sendMessage(notification);
            }

            // Cập nhật cho các thành viên hiện tại
            broadcastGroupUpdate(groupName);

            writer.println("MEMBER_ADDED:" + groupName + ":" + newMember);
            System.out.println("✓ " + newMember + " được thêm vào nhóm '" + groupName + "'");
        }

        private void handleRemoveMember(String groupName, String memberToRemove) {
            GroupInfo group = groups.get(groupName);

            if (group == null) {
                writer.println("GROUP_ERROR:Nhóm không tồn tại");
                return;
            }

            if (!group.getCreator().equals(username)) {
                writer.println("GROUP_ERROR:Chỉ người tạo mới có quyền xóa thành viên");
                return;
            }

            group.removeMember(memberToRemove);

            // Thông báo cho thành viên bị xóa
            ClientHandler removedClient = onlineUsers.get(memberToRemove);
            if (removedClient != null) {
                removedClient.sendMessage("GROUP_REMOVED:" + groupName);
            }

            // Cập nhật cho các thành viên còn lại
            broadcastGroupUpdate(groupName);

            writer.println("MEMBER_REMOVED:" + groupName + ":" + memberToRemove);
            System.out.println("✓ " + memberToRemove + " bị xóa khỏi nhóm '" + groupName + "'");
        }

        private void sendPrivateMessage(String targetUser, String content) {
            ClientHandler targetClient = onlineUsers.get(targetUser);

            if (targetClient != null) {
                targetClient.sendMessage("[" + username + " (riêng)]: " + content);
                System.out.println("PRIVATE [" + username + " -> " + targetUser + "]: " + content);
            } else {
                writer.println(">>> Lỗi: Người dùng " + targetUser + " không online!");
            }
        }

        private void sendGroupMessage(String membersStr, String content) {
            String[] members = membersStr.split(",");
            String formattedMessage = "[" + username + " -> Nhóm]: " + content;

            for (String memberName : members) {
                String trimmed = memberName.trim();
                ClientHandler targetClient = onlineUsers.get(trimmed);

                if (targetClient != null) {
                    targetClient.sendMessage(formattedMessage);
                }
            }

            System.out.println("GROUP [" + username + " -> " + membersStr + "]: " + content);
        }

        public void sendMessage(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        public String getUsername() {
            return username;
        }

        public boolean isLoggedIn() {
            return isLoggedIn;
        }

        public void disconnect() {
            try {
                if (isLoggedIn && username != null) {
                    onlineUsers.remove(username);
                    broadcastMessage(">>> " + username + " đã rời khỏi phòng chat!", null);
                    broadcastUserList();
                    System.out.println("✗ " + username + " đã ngắt kết nối");
                }

                clients.remove(this);

                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Lỗi khi ngắt kết nối: " + e.getMessage());
            }
        }
    }
}