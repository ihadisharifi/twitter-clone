package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.dao.BookmarkDao;
import server.database.dao.TweetDao;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;

public class BookmarkController {
    private final AuthController authController;
    private final BookmarkDao bookmarkDao = new BookmarkDao();
    private final TweetDao tweetDao = new TweetDao();
    private final Gson gson = new Gson();

    public BookmarkController(AuthController authController) { this.authController = authController; }

    public Response bookmark(String id, JsonElement payload) { return change(id, payload, true); }
    public Response unbookmark(String id, JsonElement payload) { return change(id, payload, false); }

    private Response change(String id, JsonElement payload, boolean add) {
        JsonObject body = payload.getAsJsonObject();
        User user = authController.requireAuth(body);
        if (user == null) return Response.error(id, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        if (!body.has("tweetId")) return Response.error(id, StatusCode.BAD_REQUEST, "tweetId is required.");
        try {
            int tweetId = body.get("tweetId").getAsInt();
            if (tweetDao.getTweet(tweetId) == null) return Response.error(id, StatusCode.NOT_FOUND, "Tweet not found.");
            if (add) bookmarkDao.bookmark(user.getId(), tweetId); else bookmarkDao.unbookmark(user.getId(), tweetId);
            return Response.ok(id, null);
        } catch (SQLException e) {
            return Response.error(id, StatusCode.SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    public Response getBookmarks(String id, JsonElement payload) {
        User user = authController.requireAuth(payload.getAsJsonObject());
        if (user == null) return Response.error(id, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        try {
            return Response.ok(id, gson.toJsonTree(bookmarkDao.getBookmarks(user.getId())));
        } catch (SQLException e) {
            return Response.error(id, StatusCode.SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }
}
