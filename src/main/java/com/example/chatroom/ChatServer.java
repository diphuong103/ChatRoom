package com.example.chatroom;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    static Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();
    private static int clientCounter = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("       CHAT SERVER - VKU");
        System.out.println("==============================================");
        System.out.println("Server đang khởi động...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✓ Server đã khởi động thành công!");
            System.out.println("✓ Đang lắng nghe trên port: " + PORT);
            System.out.println("✓ Đợi client kết nối...\n");

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientCounter++;
                    System.out.println(">>> Client #" + clientCounter + " đã kết nối từ: " +
                            clientSocket.getInetAddress().getHostAddress());

                    ClientHandler clientHandler = new ClientHandler(clientSocket, clientCounter);
                    clientHandlers.add(clientHandler);

                    Thread thread = new Thread(clientHandler);
                    thread.start();

                } catch (IOException e) {
                    System.err.println("Lỗi khi chấp nhận kết nối: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Không thể khởi động server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Gửi tin nhắn đến tất cả clients
    public static void broadcastMessage(String message, ClientHandler sender) {
        System.out.println("[BROADCAST] " + message);
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(message);
        }
    }

    // Gửi danh sách người dùng online
    public static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USERS:");
        for (ClientHandler client : clientHandlers) {
            if (client.getUsername() != null) {
                userList.append(client.getUsername()).append(",");
            }
        }

        String message = userList.toString();
        if (message.endsWith(",")) {
            message = message.substring(0, message.length() - 1);
        }

        System.out.println("[USER LIST] " + message);
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(message);
        }
    }

    // Xóa client khi ngắt kết nối
    public static void removeClient(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
        System.out.println("<<< " + clientHandler.getUsername() + " đã ngắt kết nối");
        System.out.println("Số client online: " + clientHandlers.size() + "\n");

        broadcastMessage("*** " + clientHandler.getUsername() + " đã rời khỏi phòng chat", null);
        broadcastUserList();
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private int clientId;

    public ClientHandler(Socket socket, int clientId) {
        this.socket = socket;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // Nhận username từ client
            username = reader.readLine();
            if (username == null || username.trim().isEmpty()) {
                username = "User" + clientId;
            }

            System.out.println("✓ Client #" + clientId + " đặt tên: " + username);
            System.out.println("Số client online: " + ChatServer.clientHandlers.size() + "\n");

            // Thông báo user mới vào phòng
            ChatServer.broadcastMessage("*** " + username + " đã vào phòng chat", this);
            ChatServer.broadcastUserList();

            // Nhận và xử lý tin nhắn
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("[" + username + "]: " + message);

                if (message.equals("/quit")) {
                    break;
                }

                // Broadcast tin nhắn đến tất cả clients
                String formattedMessage = "[" + username + "]: " + message;
                ChatServer.broadcastMessage(formattedMessage, this);
            }

        } catch (IOException e) {
            System.err.println("Lỗi với client " + username + ": " + e.getMessage());
        } finally {
            closeConnection();
            ChatServer.removeClient(this);
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    public String getUsername() {
        return username;
    }

    private void closeConnection() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}