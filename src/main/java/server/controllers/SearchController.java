package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.List;

public class SearchController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public SearchController(AuthController authController) {
        this.authController = authController;
    }

    public Response searchUsers(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        String query = getString(body, "query");
        if (query == null || query.trim().isEmpty()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "query is required.");
        }
        List<User> results = store.searchUsersByQuery(query.trim());
        return Response.ok(requestId, gson.toJsonTree(results));
    }

    public Response searchTweets(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        String query = getString(body, "query");
        if (query == null || query.trim().isEmpty()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "query is required.");
        }
        List<Tweet> results = store.searchTweetsByKeyword(query.trim());
        return Response.ok(requestId, gson.toJsonTree(results));
    }

    public Response searchHashtag(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        String tag = getString(body, "tag");
        if (tag == null || tag.trim().isEmpty()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "tag is required.");
        }
        String normalized = tag.startsWith("#") ? tag.substring(1) : tag;
        List<Tweet> results = store.getTweetsByHashtag(normalized.trim());
        return Response.ok(requestId, gson.toJsonTree(results));
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
