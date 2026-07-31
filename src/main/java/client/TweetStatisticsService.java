package client;

import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import shared.models.Tweet;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public final class TweetStatisticsService {

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

    public Map<Integer, Statistics> load(List<Tweet> tweets) throws Exception {
        Map<Integer, Statistics> statistics = new HashMap<>();
        Map<Integer, Integer> repostCounts = loadRepostCounts();

        for (Tweet candidate : tweets) {
            JsonObject body = authenticatedBody();
            body.addProperty("tweetId", candidate.getId());
            Response response = send(RequestType.GET_TWEET, body);
            JsonObject payload = response.getPayload().getAsJsonObject();
            Tweet detailed = gson.fromJson(payload.get("tweet"), Tweet.class);
            int replies = payload.has("replies") && payload.get("replies").isJsonArray()
                    ? payload.getAsJsonArray("replies").size() : 0;
            statistics.put(candidate.getId(), new Statistics(
                    detailed == null ? candidate : detailed,
                    replies,
                    repostCounts.getOrDefault(candidate.getId(), 0)
            ));
        }
        return statistics;
    }

    private Map<Integer, Integer> loadRepostCounts() throws Exception {
        JsonObject body = authenticatedBody();
        body.addProperty("query", "%");
        Response response = send(RequestType.SEARCH_TWEETS, body);
        Map<Integer, Integer> counts = new HashMap<>();
        if (response.getPayload() != null && response.getPayload().isJsonArray()) {
            for (JsonElement element : response.getPayload().getAsJsonArray()) {
                Tweet tweet = gson.fromJson(element, Tweet.class);
                if (tweet != null && tweet.getRetweetToId() != null) {
                    counts.merge(tweet.getRetweetToId(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private JsonObject authenticatedBody() {
        String token = UserSession.getInstance().getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Your session has expired.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        return body;
    }

    private Response send(RequestType type, JsonObject body) throws Exception {
        if (!connection.isConnected()) connection.connect();
        Response response = connection.sendMessage(new Request(
                UUID.randomUUID().toString(), type, body
        ));
        if (response == null || response.getStatus() != StatusCode.OK) {
            throw new IllegalStateException(
                    response == null || response.getMessage() == null
                            ? "Unable to load tweet statistics." : response.getMessage()
            );
        }
        return response;
    }

    public record Statistics(Tweet tweet, int repliesCount, int repostsCount) {}
}
