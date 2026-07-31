package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.dao.FollowDao;
import server.database.dao.LikeDao;
import server.database.dao.TweetDao;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;

public class LikeController {

    private final TweetDao tweetDao = new TweetDao();
    private final FollowDao followDao = new FollowDao();
    private final LikeDao likeDao = new LikeDao();
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

        try {

            int tweetId = body.get("tweetId").getAsInt();
            Tweet tweet = tweetDao.getTweet(tweetId);
            if (tweet == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found.");
            }

            likeDao.likeTweet(requester.getId(), tweetId);

            JsonObject result = new JsonObject();
            result.addProperty("liked", true);
            result.addProperty("likesCount", likeDao.getLikeCount(tweetId));
            return Response.ok(requestId, result);
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
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

        try {

            int tweetId = body.get("tweetId").getAsInt();
            likeDao.unlikeTweet(requester.getId(), tweetId);

            JsonObject result = new JsonObject();
            result.addProperty("liked", false);
            result.addProperty("likesCount", likeDao.getLikeCount(tweetId));
            return Response.ok(requestId, result);
        } catch (Exception e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }
}
