package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.ArrayList;
import java.util.List;

public class FeedController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public FeedController(AuthController authController) {
        this.authController = authController;
    }

    public Response getFeed(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        List<Tweet> feed = new ArrayList<>(store.getTweetsByAuthor(requester.getId()));
        for (Integer followedId : store.getFollowingIds(requester.getId())) {
            feed.addAll(store.getTweetsByAuthor(followedId));
        }
        feed.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (Tweet t : feed) {
            enrich(t, requester.getId());
        }
        return Response.ok(requestId, gson.toJsonTree(feed));
    }

    private void enrich(Tweet tweet, int viewerId) {
        tweet.setLikesCount(store.getLikeCount(tweet.getId()));
        tweet.setLikedByCurrentUser(store.isLikedByUser(viewerId, tweet.getId()));
        tweet.setMedia(store.getMedia(tweet.getId()));
        tweet.setHashtags(store.getHashtagsForTweet(tweet.getId()));
    }
}
