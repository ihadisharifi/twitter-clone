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

public class ProfileController {

    private final UserDao userDao = new UserDao();
    private final TweetDao tweetDao = new TweetDao();
    private final FollowDao followDao = new FollowDao();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public ProfileController(AuthController authController) {
        this.authController = authController;
    }

    public Response getProfile(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        try {
            User target = requester;
            if (body.has("username") && !body.get("username").isJsonNull()) {
                target = userDao.getUserByUsername(body.get("username").getAsString());
                if (target == null) {
                    return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
                }
            }

            JsonObject result = new JsonObject();
            result.add("user", gson.toJsonTree(target));
            result.addProperty("tweetsCount", tweetDao.getTweetCountByAuthor(target.getId()));
            result.addProperty("followersCount", followDao.getFollowerCount(target.getId()));
            result.addProperty("followingCount", followDao.getFollowingCount(target.getId()));
            return Response.ok(requestId, gson.toJsonTree(target));
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }

    public Response updateProfile(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        try {
            User updated = userDao.updateProfile(
                    requester.getId(),
                    body.has("displayName") ? body.get("displayName").getAsString() : requester.getDisplayName(),
                    body.has("bio") ? body.get("bio").getAsString() : requester.getBio(),
                    body.has("avatarUrl") ? body.get("avatarUrl").getAsString() : requester.getAvatarUrl(),
                    body.has("bannerUrl") ? body.get("bannerUrl").getAsString() : requester.getBannerUrl()
            );
            return Response.ok(requestId, gson.toJsonTree(updated));
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }
}