package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

public class LikeController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public LikeController(AuthController authController) {
        this.authController = authController;
    }

    public Response likeTweet(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        if (!body.has("tweetId") || body.get("tweetId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "tweetId is required.");
        }

        int tweetId = body.get("tweetId").getAsInt();
        Tweet tweet = store.getTweet(tweetId);
        if (tweet == null) {
            return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found.");
        }

        JsonObject result = new JsonObject();
        result.addProperty("liked", true);
        result.addProperty("likesCount", store.getLikeCount(tweetId));
        return Response.ok(requestId, result);
    }

    public Response unlikeTweet(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        if (!body.has("tweetId") || body.get("tweetId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "tweetId is required.");
        }

        int tweetId = body.get("tweetId").getAsInt();
        store.unlikeTweet(requester.getId(), tweetId);

        JsonObject result = new JsonObject();
        result.addProperty("liked", false);
        result.addProperty("likesCount", store.getLikeCount(tweetId));
        return Response.ok(requestId, result);
    }
}
