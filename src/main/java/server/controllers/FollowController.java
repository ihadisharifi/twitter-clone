package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.dao.FollowDao;
import server.database.dao.TweetDao;
import server.database.dao.UserDao;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FollowController {

    private final UserDao userDao = new UserDao();
    private final TweetDao tweetDao = new TweetDao();
    private final FollowDao followDao = new FollowDao();
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
        try {

            User target = userDao.getUserById(targetId);
            if (target == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
            }

            followDao.follow(requester.getId(), targetId);

            JsonObject result = new JsonObject();
            result.addProperty("following", true);
            result.addProperty("followerCount", followDao.getFollowerIds(targetId).size());
            return Response.ok(requestId, result);
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
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

        try {
            int targetId = body.get("userId").getAsInt();
            followDao.unfollow(requester.getId(), targetId);

            JsonObject result = new JsonObject();
            result.addProperty("following", false);
            result.addProperty("followerCount", followDao.getFollowerIds(targetId).size());
            return Response.ok(requestId, result);
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }

    public Response getFollowers(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        try {
            int targetId = body.has("userId") && !body.get("userId").isJsonNull()
                    ? body.get("userId").getAsInt() : requester.getId();
            List<User> followers = new ArrayList<>();
            for (Integer id : followDao.getFollowerIds(targetId)) {
                User u = userDao.getUserById(id);
                if (u != null) followers.add(u);
            }
            return Response.ok(requestId, gson.toJsonTree(followers));
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }

    public Response getFollowing(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        try {
            int targetId = body.has("userId") && !body.get("userId").isJsonNull()
                    ? body.get("userId").getAsInt() : requester.getId();
            List<User> following = new ArrayList<>();
            for (Integer id : followDao.getFollowingIds(targetId)) {
                User u = userDao.getUserById(id);
                if (u != null) following.add(u);
            }
            return Response.ok(requestId, gson.toJsonTree(following));
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }
}
