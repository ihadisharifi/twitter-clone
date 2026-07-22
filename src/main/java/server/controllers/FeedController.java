package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import server.database.dao.*;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FeedController {

    private final TweetDao tweetDao = new TweetDao();
    private final FollowDao followDao = new FollowDao();
    private final LikeDao likeDao = new LikeDao();
    private final MediaDao mediaDao = new MediaDao();
    private final HashtagDao hashtagDao = new HashtagDao();
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
        try {

            List<Tweet> feed = new ArrayList<>(tweetDao.getTweetsByAuthor(requester.getId()));
            for (Integer followedId : followDao.getFollowingIds(requester.getId())) {
                feed.addAll(tweetDao.getTweetsByAuthor(followedId));
            }
            feed.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

            for (Tweet t : feed) {
                enrich(t, requester.getId());
            }
            return Response.ok(requestId, gson.toJsonTree(feed));
        } catch (SQLException e) {
            return Response.error(requestId,StatusCode.SERVER_ERROR,"database error: "+e.getMessage());
        }
    }

    private void enrich(Tweet tweet, int viewerId) throws SQLException {
            tweet.setLikesCount(likeDao.getLikeCount(tweet.getId()));
            tweet.setLikedByCurrentUser(likeDao.isLikedByUser(viewerId, tweet.getId()));
            tweet.setMedia(mediaDao.getMedia(tweet.getId()));
            tweet.setHashtags(hashtagDao.getHashtagsForTweet(tweet.getId()));
    }
}
