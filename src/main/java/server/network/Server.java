package server.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import server.database.DatabaseInitializer;


public class Server {

    private  static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Server Started");
        DatabaseInitializer.run();
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted connection from " + socket.getInetAddress().getHostName());
                pool.execute(new ClientHandler(socket));
            }
        } catch (IOException e)  {
            e.printStackTrace();
        }
        finally {
            pool.shutdown();
        }
    }
}
