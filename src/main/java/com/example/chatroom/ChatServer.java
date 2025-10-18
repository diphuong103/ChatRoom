//package com.example.chatroom;
//
//import java.io.*;
//import java.net.*;
//import java.util.*;
//import java.util.concurrent.*;
//
//public class ChatServer {
//    private static final int PORT = 12345;
//    static Map<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();
//    static Map<String, String> userDatabase = new ConcurrentHashMap<>();
//    private static int clientCounter = 0;
//
//    public static void main(String[] args) {
//        System.out.println("==============================================");
//        System.out.println("       VKU CHAT SERVER - MULTICAST");
//        System.out.println("==============================================");
//        System.out.println("Server đang khởi động...");
//
//        // Load user database
//        loadUserDatabase();
//
//        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
//            System.out.println("✓ Server đã khởi động thành công!");
//            System.out.println("✓ Đang lắng nghe trên port: " + PORT);
//            System.out.println("✓ Đợi client kết nối...\n");
//
//            while (true) {
//                try {
//                    Socket clientSocket = serverSocket.accept();
//                    clientCounter++;
//                    System.out.println(">>> Client #" + clientCounter + " đã kết nối từ: " +
//                            clientSocket.getInetAddress().getHostAddress());
//
//                    Thread thread = new Thread(() -> handleClient(clientSocket, clientCounter));
//                    thread.start();
//
//                } catch (IOException e) {
//                    System.err.println("Lỗi khi chấp nhận kết nối: " + e.getMessage());
//                }
//            }
//
//        } catch (IOException e) {
//            System.err.println("Không thể khởi động server: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    private static void handleClient(Socket clientSocket, int clientId) {
//        try {
//            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
//
//            // Nhận loại yêu cầu (LOGIN hoặc REGISTER)
//            String requestType = reader.readLine();
//
//            if ("REGISTER".equals(requestType)) {
//                handleRegister(reader, writer, clientSocket);
//            } else if ("LOGIN".equals(requestType)) {
//                handleLogin(reader, writer, clientSocket, clientId);
//            }
//
//        } catch (IOException e) {
//            System.err.println("Lỗi xử lý client: " + e.getMessage());
//        }
//    }
//
//    private static void handleRegister(BufferedReader reader, PrintWriter writer, Socket socket) {
//        try {
//            String username = reader.readLine();
//            String hashedPassword = reader.readLine();
//
//            if (userDatabase.containsKey(username)) {
//                writer.println("REGISTER_FAILED");
//                System.out.println(">>> Đăng ký thất bại: " + username + " (đã tồn tại)");
//            } else {
//                userDatabase.put(username, hashedPassword);
//                saveUserDatabase();
//                writer.println("REGISTER_SUCCESS");
//                System.out.println("✓ Đăng ký thành công: " + username);
//            }
//            socket.close();
//
//        } catch (IOException e) {
//            System.err.println("Lỗi xử lý đăng ký: " + e.getMessage());
//        }
//    }
//
//    private static void handleLogin(BufferedReader reader, PrintWriter writer, Socket socket, int clientId) {
//        try {
//            String username = reader.readLine();
//            String hashedPassword = reader.readLine();
//
//            if (!userDatabase.containsKey(username)) {
//                writer.println("LOGIN_FAILED");
//                System.out.println(">>> Đăng nhập thất bại: " + username + " (không tồn tại)");
//                socket.close();
//                return;
//            }
//
//            if (!userDatabase.get(username).equals(hashedPassword)) {
//                writer.println("LOGIN_FAILED");
//                System.out.println(">>> Đăng nhập thất bại: " + username + " (sai mật khẩu)");
//                socket.close();
//                return;
//            }
//
//            if (clientHandlers.containsKey(username)) {
//                writer.println("USER_ALREADY_ONLINE");
//                System.out.println(">>> Đăng nhập thất bại: " + username + " (đã online)");
//                socket.close();
//                return;
//            }
//
//            writer.println("LOGIN_SUCCESS");
//            System.out.println("✓ Đăng nhập thành công: " + username);
//
//            ClientHandler clientHandler = new ClientHandler(socket, username, clientId, reader, writer);
//            clientHandlers.put(username, clientHandler);
//
//            Thread thread = new Thread(clientHandler);
//            thread.start();
//
//        } catch (IOException e) {
//            System.err.println("Lỗi xử lý đăng nhập: " + e.getMessage());
//        }
//    }
//
//    public static void broadcastMessage(String message, String senderUsername) {
//        System.out.println("[BROADCAST] " + message);
//        for (ClientHandler client : clientHandlers.values()) {
//            client.sendMessage(message);
//        }
//    }
//
//    public static void sendPrivateMessage(String fromUser, String toUser, String message) {
//        ClientHandler recipient = clientHandlers.get(toUser);
//        ClientHandler sender = clientHandlers.get(fromUser);
//
//        if (recipient != null) {
//            recipient.sendMessage("[" + fromUser + " -> Bạn (riêng)]: " + message);
//            System.out.println("[PRIVATE] " + fromUser + " -> " + toUser + ": " + message);
//        } else if (sender != null) {
//            sender.sendMessage(">>> Lỗi: Người dùng " + toUser + " không online!");
//        }
//    }
//
//    public static void sendGroupMessage(String fromUser, String[] members, String message) {
//        System.out.println("[GROUP] " + fromUser + " -> [" + String.join(",", members) + "]: " + message);
//
//        ClientHandler sender = clientHandlers.get(fromUser);
//        int sentCount = 0;
//
//        for (String member : members) {
//            member = member.trim();
//            if (!member.equals(fromUser)) {
//                ClientHandler recipient = clientHandlers.get(member);
//                if (recipient != null) {
//                    recipient.sendMessage("[" + fromUser + " -> Nhóm]: " + message);
//                    sentCount++;
//                }
//            }
//        }
//
//        if (sender != null && sentCount == 0) {
//            sender.sendMessage(">>> Lỗi: Không có thành viên nào trong nhóm online!");
//        }
//    }
//
//    public static void broadcastUserList() {
//        StringBuilder userList = new StringBuilder("USERS:");
//        for (String username : clientHandlers.keySet()) {
//            userList.append(username).append(",");
//        }
//
//        String message = userList.toString();
//        if (message.endsWith(",")) {
//            message = message.substring(0, message.length() - 1);
//        }
//
//        System.out.println("[USER LIST] " + message);
//        for (ClientHandler client : clientHandlers.values()) {
//            client.sendMessage(message);
//        }
//    }
//
//    public static void removeClient(String username) {
//        clientHandlers.remove(username);
//        System.out.println("<<< " + username + " đã ngắt kết nối");
//        System.out.println("Số client online: " + clientHandlers.size() + "\n");
//
//        broadcastMessage("*** " + username + " đã rời khỏi phòng chat", null);
//        broadcastUserList();
//    }
//
//    private static void loadUserDatabase() {
//        try {
//            File file = new File("users.dat");
//            if (file.exists()) {
//                BufferedReader br = new BufferedReader(new FileReader(file));
//                String line;
//                while ((line = br.readLine()) != null) {
//                    String[] parts = line.split(":");
//                    if (parts.length == 2) {
//                        userDatabase.put(parts[0], parts[1]);
//                    }
//                }
//                br.close();
//                System.out.println("✓ Đã load " + userDatabase.size() + " tài khoản từ database");
//            } else {
//                System.out.println("✓ Tạo database mới");
//            }
//        } catch (IOException e) {
//            System.err.println("Lỗi load database: " + e.getMessage());
//        }
//    }
//
//    private static void saveUserDatabase() {
//        try {
//            PrintWriter pw = new PrintWriter(new FileWriter("users.dat"));
//            for (Map.Entry<String, String> entry : userDatabase.entrySet()) {
//                pw.println(entry.getKey() + ":" + entry.getValue());
//            }
//            pw.close();
//        } catch (IOException e) {
//            System.err.println("Lỗi save database: " + e.getMessage());
//        }
//    }
//}
//
//class ClientHandler implements Runnable {
//    private Socket socket;
//    private BufferedReader reader;
//    private PrintWriter writer;
//    private String username;
//    private int clientId;
//
//    public ClientHandler(Socket socket, String username, int clientId,
//                         BufferedReader reader, PrintWriter writer) {
//        this.socket = socket;
//        this.username = username;
//        this.clientId = clientId;
//        this.reader = reader;
//        this.writer = writer;
//    }
//
//    @Override
//    public void run() {
//        try {
//            System.out.println("✓ Client #" + clientId + " (" + username + ") đã sẵn sàng");
//            System.out.println("Số client online: " + ChatServer.clientHandlers.size() + "\n");
//
//            ChatServer.broadcastMessage("*** " + username + " đã vào phòng chat", username);
//            ChatServer.broadcastUserList();
//
//            String message;
//            while ((message = reader.readLine()) != null) {
//                if (message.equals("/quit")) {
//                    break;
//                }
//
//                processMessage(message);
//            }
//
//        } catch (IOException e) {
//            System.err.println("Lỗi với client " + username + ": " + e.getMessage());
//        } finally {
//            closeConnection();
//            ChatServer.removeClient(username);
//        }
//    }
//
//    private void processMessage(String message) {
//        try {
//            if (message.startsWith("PUBLIC:")) {
//                String content = message.substring(7);
//                String formattedMessage = "[" + username + "]: " + content;
//                ChatServer.broadcastMessage(formattedMessage, username);
//
//            } else if (message.startsWith("PRIVATE:")) {
//                String[] parts = message.substring(8).split(":", 2);
//                if (parts.length == 2) {
//                    String toUser = parts[0];
//                    String content = parts[1];
//                    ChatServer.sendPrivateMessage(username, toUser, content);
//                }
//
//            } else if (message.startsWith("GROUP:")) {
//                String[] parts = message.substring(6).split(":", 2);
//                if (parts.length == 2) {
//                    String[] members = parts[0].split(",");
//                    String content = parts[1];
//                    ChatServer.sendGroupMessage(username, members, content);
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Lỗi xử lý tin nhắn từ " + username + ": " + e.getMessage());
//        }
//    }
//
//    public void sendMessage(String message) {
//        if (writer != null) {
//            writer.println(message);
//        }
//    }
//
//    public String getUsername() {
//        return username;
//    }
//
//    private void closeConnection() {
//        try {
//            if (reader != null) reader.close();
//            if (writer != null) writer.close();
//            if (socket != null) socket.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}