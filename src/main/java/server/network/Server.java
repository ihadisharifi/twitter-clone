package server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private  static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Server Started");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted connection from " + socket.getInetAddress().getHostName());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e)  {
            e.printStackTrace();
        }
    }
}
