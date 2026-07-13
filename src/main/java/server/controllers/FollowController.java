package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.ArrayList;
import java.util.List;

public class FollowController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public FollowController(AuthController authController) {
        this.authController = authController;
    }

    public Response follow(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        if (!body.has("userId") || body.get("userId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "userId is required.");
        }

        int targetId = body.get("userId").getAsInt();
        if (targetId == requester.getId()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "You cannot follow yourself.");
        }
        User target = store.getUserById(targetId);
        if (target == null) {
            return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
        }

        store.follow(requester.getId(), targetId);

        JsonObject result = new JsonObject();
        result.addProperty("following", true);
        result.addProperty("followerCount", store.getFollowerIds(targetId).size());
        return Response.ok(requestId, result);
    }

    public Response unfollow(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        if (!body.has("userId") || body.get("userId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "userId is required.");
        }

        int targetId = body.get("userId").getAsInt();
        store.unfollow(requester.getId(), targetId);

        JsonObject result = new JsonObject();
        result.addProperty("following", false);
        result.addProperty("followerCount", store.getFollowerIds(targetId).size());
        return Response.ok(requestId, result);
    }

    public Response getFollowers(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        int targetId = body.has("userId") && !body.get("userId").isJsonNull()
                ? body.get("userId").getAsInt() : requester.getId();

        List<User> followers = new ArrayList<>();
        for (Integer id : store.getFollowerIds(targetId)) {
            User u = store.getUserById(id);
            if (u != null) followers.add(u);
        }
        return Response.ok(requestId, gson.toJsonTree(followers));
    }

    public Response getFollowing(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        int targetId = body.has("userId") && !body.get("userId").isJsonNull()
                ? body.get("userId").getAsInt() : requester.getId();

        List<User> following = new ArrayList<>();
        for (Integer id : store.getFollowingIds(targetId)) {
            User u = store.getUserById(id);
            if (u != null) following.add(u);
        }
        return Response.ok(requestId, gson.toJsonTree(following));
    }
}
