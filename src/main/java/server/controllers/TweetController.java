package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import server.network.ConnectionRegistry;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        Integer retweetToId = body.has("retweetToId") && !body.get("retweetToId").isJsonNull()
                ? body.get("retweetToId").getAsInt() : null;

        Tweet tweet = store.createTweet(requester.getId(), content, replyToId, retweetToId);

        store.extractAndStoreHashtags(tweet.getId(), content);

        if (body.has("mediaUrls") && body.get("mediaUrls").isJsonArray()) {
            List<String> urls = new ArrayList<>();
            for (JsonElement el : body.getAsJsonArray("mediaUrls")) {
                if (!el.isJsonNull()) urls.add(el.getAsString());
            }
            store.attachMedia(tweet.getId(), urls);
        }

        enrich(tweet, requester.getId());
        JsonObject tweetJson = gson.toJsonTree(tweet).getAsJsonObject();
        notifyFollowersOfNewTweet(requester.getId(), tweetJson);
        return Response.ok(requestId, tweetJson);
    }

    private void notifyFollowersOfNewTweet(int authorId, JsonObject tweetJson) {
        Set<Integer> followerIds = store.getFollowerIds(authorId);
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

        boolean deleted = store.deleteTweet(body.get("tweetId").getAsInt(), requester.getId());
        if (!deleted) {
            return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found or not owned by you.");
        }
        return Response.ok(requestId, null);
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

        Tweet tweet = store.getTweet(body.get("tweetId").getAsInt());
        if (tweet == null) {
            return Response.error(requestId, StatusCode.NOT_FOUND, "Tweet not found.");
        }
        enrich(tweet, requester.getId());

        List<Tweet> replies = store.getRepliesTo(tweet.getId());
        for (Tweet reply : replies) enrich(reply, requester.getId());

        JsonObject result = new JsonObject();
        result.add("tweet", gson.toJsonTree(tweet));
        result.add("replies", gson.toJsonTree(replies));
        return Response.ok(requestId, result);
    }

    public Response getUserTweets(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        int targetId = requester.getId();
        if (body.has("username") && !body.get("username").isJsonNull()) {
            User target = store.getUserByUsername(body.get("username").getAsString());
            if (target == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
            }
            targetId = target.getId();
        }

        List<Tweet> tweets = store.getTweetsByAuthor(targetId);
        for (Tweet t : tweets) enrich(t, requester.getId());
        return Response.ok(requestId, gson.toJsonTree(tweets));
    }

    private void enrich(Tweet tweet, int viewerId) {
        tweet.setLikesCount(store.getLikeCount(tweet.getId()));
        tweet.setLikedByCurrentUser(store.isLikedByUser(viewerId, tweet.getId()));
        tweet.setMedia(store.getMedia(tweet.getId()));
        tweet.setHashtags(store.getHashtagsForTweet(tweet.getId()));
    }
}
