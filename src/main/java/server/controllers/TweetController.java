package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.network.ConnectionRegistry;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import server.database.dao.*;

public class TweetController {

    private static final int MAX_TWEET_CHARACTERS = 280;

    private final UserDao userDao = new UserDao();
    private final TweetDao tweetDao = new TweetDao();
    private final LikeDao likeDao = new LikeDao();
    private final FollowDao followDao = new FollowDao();
    private final HashtagDao hashtagDao = new HashtagDao();
    private final MediaDao mediaDao = new MediaDao();
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
        if (content.codePointCount(0, content.length()) > MAX_TWEET_CHARACTERS) {
            return Response.error(requestId, StatusCode.BAD_REQUEST,
                    "Tweets cannot exceed " + MAX_TWEET_CHARACTERS + " characters.");
        }

        try {
            Integer replyToId = body.has("replyToId") && !body.get("replyToId").isJsonNull()
                    ? body.get("replyToId").getAsInt() : null;
            Integer retweetToId = body.has("retweetToId") && !body.get("retweetToId").isJsonNull()
                    ? body.get("retweetToId").getAsInt() : null;

            Tweet tweet = tweetDao.createTweet(requester.getId(), content, replyToId, retweetToId);

            hashtagDao.extractAndStoreHashtags(tweet.getId(), content);

            if (body.has("mediaUrls") && body.get("mediaUrls").isJsonArray()) {
                List<String> urls = new ArrayList<>();
                for (JsonElement el : body.getAsJsonArray("mediaUrls")) {
                    if (!el.isJsonNull()) urls.add(el.getAsString());
                }
                mediaDao.attachMedia(tweet.getId(), urls);
            }

            enrich(tweet, requester.getId());
            JsonObject tweetJson = gson.toJsonTree(tweet).getAsJsonObject();
            notifyFollowersOfNewTweet(requester.getId(), tweetJson);
            return Response.ok(requestId, tweetJson);
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "Database error: "+e.getMessage());
        }
    }

    private void notifyFollowersOfNewTweet(int authorId, JsonObject tweetJson) throws SQLException {
        Set<Integer> followerIds = followDao.getFollowerIds(authorId);
        if (followerIds.isEmpty()) return;

        Response event = Response.event("NEW_TWEET", tweetJson);
        ConnectionRegistry registry = ConnectionRegistry.get();
        for (Integer followerId : followerIds) {
            registry.sendTo(followerId, event);
        }
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

        try {
            boolean deleted = tweetDao.deleteTweet(body.get("tweetId").getAsInt(), requester.getId());
            if (!deleted) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found or not owned by you.");
            }
            return Response.ok(requestId, null);
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "database error: "+e.getMessage());
        }
    }

    public Response getTweet(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }
        if (!body.has("tweetId") || body.get("tweetId").isJsonNull()) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "tweetId is required.");
        }

        try {
            Tweet tweet = tweetDao.getTweet(body.get("tweetId").getAsInt());
            if (tweet == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found.");
            }
            enrich(tweet, requester.getId());

            List<Tweet> replies = tweetDao.getRepliesTo(tweet.getId());
            for (Tweet reply : replies) enrich(reply, requester.getId());

            JsonObject result = new JsonObject();
            result.add("tweet", gson.toJsonTree(tweet));
            result.add("replies", gson.toJsonTree(replies));
            return Response.ok(requestId, result);
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "database error: "+e.getMessage());
        }
    }

    public Response getUserTweets(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        try {
            int targetId = requester.getId();
            if (body.has("username") && !body.get("username").isJsonNull()) {
                User target = userDao.getUserByUsername(body.get("username").getAsString());
                if (target == null) {
                    return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
                }
                targetId = target.getId();
            }

            List<Tweet> tweets = tweetDao.getTweetsByAuthor(targetId);
            for (Tweet t : tweets) enrich(t, requester.getId());
            return Response.ok(requestId, gson.toJsonTree(tweets));
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "database error: "+e.getMessage());
        }
    }

    private void enrich(Tweet tweet, int viewerId) throws SQLException {
        tweet.setLikesCount(likeDao.getLikeCount(tweet.getId()));
        tweet.setLikedByCurrentUser(likeDao.isLikedByUser(viewerId, tweet.getId()));
        tweet.setMedia(mediaDao.getMedia(tweet.getId()));
        tweet.setHashtags(hashtagDao.getHashtagsForTweet(tweet.getId()));
    }
}
