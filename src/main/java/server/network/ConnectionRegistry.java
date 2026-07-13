package server.network;

import shared.protocol.MessageCodec;
import shared.protocol.Response;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionRegistry {

    private static final ConnectionRegistry INSTANCE = new ConnectionRegistry();
    public static ConnectionRegistry get() { return INSTANCE; }
    private ConnectionRegistry() {}

    private final Map<Integer, PrintWriter> writersByUserId = new ConcurrentHashMap<>();

    public void register(int userId, PrintWriter out) {
        writersByUserId.put(userId, out);
    }

    public void unregister(int userId) {
        writersByUserId.remove(userId);
    }

    public boolean isOnline(int userId) {
        return writersByUserId.containsKey(userId);
    }

    public void sendTo(int userId, Response event) {
        PrintWriter out = writersByUserId.get(userId);
        if (out == null) return;

        String json = MessageCodec.encodeResponse(event);
        synchronized (out) {
            out.println(json);
        }
    }
}