package server.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import server.controllers.*;
import shared.protocol.MessageCodec;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;

    private Integer connectedUserId = null;

    private final AuthController authController = new AuthController();
    private final ProfileController profileController = new ProfileController(authController);
    private final TweetController tweetController = new TweetController(authController);
    private final FeedController feedController = new FeedController(authController);
    private final FollowController followController = new FollowController(authController);
    private final LikeController likeController = new LikeController(authController);
    private final SearchController searchController = new SearchController(authController);

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = writer;

            String raw;
            while ((raw = in.readLine()) != null) {
                Request request = MessageCodec.decodeRequest(raw);
                System.out.println("Received: " + request.getType());

                Response response = dispatch(request);
                writeToClient(response);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            if (connectedUserId != null) {
                ConnectionRegistry.get().unregister(connectedUserId);
            }
        }
    }

    private void writeToClient(Response response) {
        String json = MessageCodec.encodeResponse(response);
        synchronized (out) {
            out.println(json);
        }
    }

    private Response dispatch(Request request) {
        try {
            switch (request.getType()) {
                case PING:
                    return Response.ok(request.getRequestId(), JsonParser.parseString("\"pong\""));
                case REGISTER: {
                    Response resp = authController.register(request.getRequestId(), request.getPayload());
                    registerConnectionIfSuccessful(resp);
                    return resp;
                }
                case LOGIN: {
                    Response resp = authController.login(request.getRequestId(), request.getPayload());
                    registerConnectionIfSuccessful(resp);
                    return resp;
                }
                case LOGOUT:
                    if (connectedUserId != null) {
                        ConnectionRegistry.get().unregister(connectedUserId);
                        connectedUserId = null;
                    }
                    return authController.logout(request.getRequestId(), request.getPayload());
                case GET_PROFILE:
                    return profileController.getProfile(request.getRequestId(), request.getPayload());
                case UPDATE_PROFILE:
                    return profileController.updateProfile(request.getRequestId(), request.getPayload());
                case CREATE_TWEET:
                    return tweetController.createTweet(request.getRequestId(), request.getPayload());
                case DELETE_TWEET:
                    return tweetController.deleteTweet(request.getRequestId(), request.getPayload());
                case GET_TWEET:
                    return tweetController.getTweet(request.getRequestId(), request.getPayload());
                case GET_USER_TWEETS:
                    return tweetController.getUserTweets(request.getRequestId(), request.getPayload());
                case FOLLOW:
                    return followController.follow(request.getRequestId(), request.getPayload());
                case UNFOLLOW:
                    return followController.unfollow(request.getRequestId(), request.getPayload());
                case GET_FOLLOWERS:
                    return followController.getFollowers(request.getRequestId(), request.getPayload());
                case GET_FOLLOWING:
                    return followController.getFollowing(request.getRequestId(), request.getPayload());
                case LIKE_TWEET:
                    return likeController.likeTweet(request.getRequestId(), request.getPayload());
                case UNLIKE_TWEET:
                    return likeController.unlikeTweet(request.getRequestId(), request.getPayload());
                case GET_FEED:
                    return feedController.getFeed(request.getRequestId(), request.getPayload());
                case SEARCH_USERS:
                    return searchController.searchUsers(request.getRequestId(), request.getPayload());
                case SEARCH_TWEETS:
                    return searchController.searchTweets(request.getRequestId(), request.getPayload());
                case SEARCH_HASHTAG:
                    return searchController.searchHashtag(request.getRequestId(), request.getPayload());
                default:
                    return Response.error(request.getRequestId(), StatusCode.BAD_REQUEST, "Unsupported request type.");
            }
        } catch (Exception e) {
            return Response.error(request.getRequestId(), StatusCode.SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    private void registerConnectionIfSuccessful(Response resp) {
        if (resp.getStatus() != StatusCode.OK || resp.getPayload() == null) return;
        try {
            JsonObject payload = resp.getPayload().getAsJsonObject();
            int userId = payload.getAsJsonObject("user").get("id").getAsInt();
            this.connectedUserId = userId;
            ConnectionRegistry.get().register(userId, out);
        } catch (Exception ignored) {
        }
    }
}