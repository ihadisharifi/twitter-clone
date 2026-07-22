package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.dao.HashtagDao;
import server.database.dao.TweetDao;
import server.database.dao.UserDao;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.List;

public class SearchController {

    private final UserDao userDao = new UserDao();
    private final TweetDao tweetDao = new TweetDao();
    private final HashtagDao hashtagDao = new HashtagDao();
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

        try {
            List<User> results = userDao.searchUsersByQuery(query.trim());
            return Response.ok(requestId, gson.toJsonTree(results));
        } catch (Exception e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
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

        try {
            List<Tweet> results = tweetDao.searchTweetsByKeyword(query.trim());
            return Response.ok(requestId, gson.toJsonTree(results));
        } catch (Exception e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
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

        try {
            String normalized = tag.startsWith("#") ? tag.substring(1) : tag;
            List<Tweet> results = hashtagDao.getTweetsByHashtag(normalized.trim());
            return Response.ok(requestId, gson.toJsonTree(results));
        } catch (Exception e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR,"Database error: "+e.getMessage());
        }
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
