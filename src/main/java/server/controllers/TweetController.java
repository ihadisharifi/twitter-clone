package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

public class TweetController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public TweetController(AuthController authController) {
        this.authController = authController;
    }


    public Response createTweet(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        String content = body.has("content") && !body.get("content").isJsonNull()
                ? body.get("content").getAsString() : null;
        if (content == null || content.trim().isEmpty()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "Tweet content cannot be empty.");
        }

        Integer replyToId = body.has("replyToId") && !body.get("replyToId").isJsonNull()
                ? body.get("replyToId").getAsInt() : null;

        Tweet tweet = store.createTweet(requester.getId(), content, replyToId, null);
        return Response.ok(requestId, gson.toJsonTree(tweet));
    }
    
    public Response deleteTweet(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        if (!body.has("tweetId") || body.get("tweetId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "tweetId is required.");
        }

        boolean deleted = store.deleteTweet(body.get("tweetId").getAsInt(), requester.getId());
        if (!deleted) {
            return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found or not owned by you.");
        }
        return Response.ok(requestId, null);
    }
}