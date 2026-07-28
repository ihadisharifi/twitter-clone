package client.network;

import shared.protocol.MessageCodec;
import shared.protocol.Request;
import shared.protocol.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;


public class ServerConnection {

    private static final String HOST = "localhost";
    private static final int PORT = 8090;
    private static final long RESPONSE_TIMEOUT_SECONDS = 10;

    private static final ServerConnection INSTANCE = new ServerConnection();

    public static ServerConnection getInstance() {
        return INSTANCE;
    }

    public ServerConnection() {}

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private volatile boolean running = false;
    private final Map<String, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
    private volatile Consumer<Response> eventListener;

    public synchronized void connect() throws IOException {
        if (running) {
            return;
        }

        socket = new Socket(HOST, PORT);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        running = true;
        listenerThread = new Thread(this::listenLoop, "server-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        System.out.println("Connected to server");
    }

    public void setEventListener(Consumer<Response> listener) {
        this.eventListener = listener;
    }

    public Response sendMessage(Request req) throws IOException {
        if (!running) {
            throw new IOException("Not connected to server");
        }

        CompletableFuture<Response> future = new CompletableFuture<>();
        pending.put(req.getRequestId(), future);

        String jsonPayload = MessageCodec.encodeRequest(req);
        synchronized (out) {
            out.println(jsonPayload);
        }

        try {
            return future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(req.getRequestId());
            throw new IOException("Timed out waiting for server response", e);
        } catch (InterruptedException e) {
            pending.remove(req.getRequestId());
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for server response", e);
        } catch (ExecutionException e) {
            pending.remove(req.getRequestId());
            throw new IOException("Request failed", e);
        }
    }

    private void listenLoop() {
        try {
            String raw;
            while (running && (raw = in.readLine()) != null) {
                Response response;
                try {
                    response = MessageCodec.decodeResponse(raw);
                } catch (Exception e) {
                    System.err.println("Failed to decode server message: " + raw);
                    continue;
                }

                if (response.isEvent()) {
                    Consumer<Response> listener = eventListener;
                    if (listener != null) {
                        listener.accept(response);
                    }
                } else {
                    CompletableFuture<Response> future = pending.remove(response.getRequestId());
                    if (future != null) {
                        future.complete(response);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Connection to server lost: " + e.getMessage());
            }
        } finally {
            running = false;
            failAllPending(new IOException("Connection closed"));
        }
    }

    private void failAllPending(IOException cause) {
        for (CompletableFuture<Response> future : pending.values()) {
            future.completeExceptionally(cause);
        }
        pending.clear();
    }

    public boolean isConnected() {
        return running;
    }

    public synchronized void disconnect() throws IOException {
        running = false;
        failAllPending(new IOException("Disconnected"));
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}