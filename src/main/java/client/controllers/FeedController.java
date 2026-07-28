package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetMediaHelper;
import client.TweetTimeFormatter;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class FeedController {

    @FXML private VBox timelineContainer;
    @FXML private HBox drawerOverlay;
    @FXML private VBox drawerPanel;
    @FXML private Label drawerDisplayName;
    @FXML private Label drawerUsername;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadTimeline();
    }

    private void loadTimeline() {
        showLoadingState();

        Task<FeedData> task = new Task<>() {
            @Override
            protected FeedData call() throws Exception {
                return fetchFeed();
            }
        };

        task.setOnSucceeded(event -> renderTimeline(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            showErrorState(error == null ? "Unable to load your feed." : error.getMessage());
        });

        Thread thread = new Thread(task, "load-home-feed");
        thread.setDaemon(true);
        thread.start();
    }

    private FeedData fetchFeed() throws Exception {
        if (!connection.isConnected()) {
            connection.connect();
        }

        String token = UserSession.getInstance().getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Your session has expired. Please sign in again.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("token", token);

        RequestType feedType;
        try {
            feedType = RequestType.valueOf("GET_FEED");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "RequestType.GET_FEED is missing. Add it to the shared RequestType enum."
            );
        }

        Request request = new Request(UUID.randomUUID().toString(), feedType, body);
        Response response = connection.sendMessage(request);

        if (response == null) {
            throw new IllegalStateException("The server returned no response.");
        }
        if (response.getStatus() != StatusCode.OK) {
            throw new IllegalStateException(
                    response.getMessage() == null ? "Unable to load your feed." : response.getMessage()
            );
        }
        if (response.getPayload() == null || response.getPayload().isJsonNull()) {
            return new FeedData(List.of(), Map.of());
        }

        return parseFeedPayload(response.getPayload());
    }

    private FeedData parseFeedPayload(JsonElement payload) {
        JsonArray tweetArray;
        Map<Integer, User> usersById = new HashMap<>();

        if (payload.isJsonArray()) {
            tweetArray = payload.getAsJsonArray();
        } else if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            tweetArray = object.has("tweets") && object.get("tweets").isJsonArray()
                    ? object.getAsJsonArray("tweets")
                    : new JsonArray();

            if (object.has("users") && object.get("users").isJsonArray()) {
                User[] users = gson.fromJson(object.get("users"), User[].class);
                if (users != null) {
                    for (User user : users) {
                        usersById.put(user.getId(), user);
                    }
                }
            }
        } else {
            tweetArray = new JsonArray();
        }

        List<Tweet> tweets = new ArrayList<>();
        for (JsonElement element : tweetArray) {
            Tweet tweet = gson.fromJson(element, Tweet.class);
            if (tweet != null) {
                tweets.add(tweet);
            }
        }
        
        tweets.sort(Comparator.comparing(this::createdAtOrEpoch).reversed());

        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null) {
            usersById.putIfAbsent(currentUser.getId(), currentUser);
        }

        return new FeedData(tweets, usersById);
    }

    private void renderTimeline(FeedData feed) {
        timelineContainer.getChildren().clear();

        if (feed.tweets().isEmpty()) {
            showEmptyState();
            return;
        }

        for (Tweet tweet : feed.tweets()) {
            User author = feed.usersById().get(tweet.getAuthorId());
            timelineContainer.getChildren().add(createTweetCard(tweet, author));
        }
    }

    private Node createTweetCard(Tweet tweet, User author) {
        VBox card = new VBox(6);
        card.setStyle(
                "-fx-border-color: #333333; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-padding: 12 16;"
        );

        HBox row = new HBox(12);

        Label avatar = new Label("👤");
        avatar.setFont(Font.font(24));
        avatar.setTextFill(Color.web("#71767b"));

        VBox content = new VBox(4);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox header = new HBox(8);

        Label displayName = new Label(displayName(author, tweet));
        displayName.setTextFill(Color.WHITE);
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label username = new Label("@" + username(author, tweet));
        username.setTextFill(Color.web("#71767b"));
        username.setFont(Font.font("System", 14));

        Label timestamp = new Label(formatTime(tweet.getCreatedAt()));
        timestamp.setTextFill(Color.web("#71767b"));
        timestamp.setFont(Font.font("System", 14));

        header.getChildren().addAll(displayName, username, timestamp);

        Label text = new Label(tweet.getContent() == null ? "" : tweet.getContent());
        text.setTextFill(Color.web("#e7e9ea"));
        text.setFont(Font.font("System", 15));
        text.setWrapText(true);
        text.setMaxWidth(420);

        content.getChildren().addAll(header, text);

        if (tweet.getMedia() != null && !tweet.getMedia().isEmpty()) {
            String mediaUrl = tweet.getMedia().get(0).getUrl();
            Node media = TweetMediaHelper.createMediaNode(mediaUrl, 380, 220);
            if (media != null) {
                content.getChildren().add(media);
            }
        }

        row.getChildren().addAll(avatar, content);
        card.getChildren().add(row);
        return card;
    }

    private String displayName(User author, Tweet tweet) {
        if (author != null && author.getDisplayName() != null && !author.getDisplayName().isBlank()) {
            return author.getDisplayName();
        }

        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getId() == tweet.getAuthorId()) {
            String value = currentUser.getDisplayName();
            return value == null || value.isBlank() ? "You" : value;
        }

        return "User " + tweet.getAuthorId();
    }

    private String username(User author, Tweet tweet) {
        if (author != null && author.getUsername() != null && !author.getUsername().isBlank()) {
            return author.getUsername();
        }

        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getId() == tweet.getAuthorId()) {
            String value = currentUser.getUsername();
            return value == null || value.isBlank() ? "user" + tweet.getAuthorId() : value;
        }

        return "user" + tweet.getAuthorId();
    }

    private Instant createdAtOrEpoch(Tweet tweet) {
        String value = tweet.getCreatedAt();
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private String formatTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return TweetTimeFormatter.formatFeedDot(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private void showLoadingState() {
        timelineContainer.getChildren().clear();
        Label loading = new Label("Loading your feed...");
        loading.setTextFill(Color.web("#71767b"));
        loading.setStyle("-fx-padding: 40 16; -fx-font-size: 15px;");
        timelineContainer.getChildren().add(loading);
    }

    private void showEmptyState() {
        timelineContainer.getChildren().clear();

        VBox box = new VBox(8);
        box.setStyle("-fx-padding: 70 40; -fx-alignment: center;");

        Label title = new Label("Your timeline is empty");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label subtitle = new Label("Post something or follow people to see their tweets here.");
        subtitle.setTextFill(Color.web("#71767b"));
        subtitle.setWrapText(true);

        box.getChildren().addAll(title, subtitle);
        timelineContainer.getChildren().add(box);
    }

    private void showErrorState(String message) {
        Platform.runLater(() -> {
            timelineContainer.getChildren().clear();

            VBox box = new VBox(8);
            box.setStyle("-fx-padding: 70 40; -fx-alignment: center;");

            Label title = new Label("Couldn't load your feed");
            title.setTextFill(Color.WHITE);
            title.setFont(Font.font("System", FontWeight.BOLD, 20));

            Label subtitle = new Label(message == null ? "Unknown server error." : message);
            subtitle.setTextFill(Color.web("#f4212e"));
            subtitle.setWrapText(true);

            box.getChildren().addAll(title, subtitle);
            timelineContainer.getChildren().add(box);
        });
    }

    private record FeedData(List<Tweet> tweets, Map<Integer, User> usersById) {}

    @FXML private void handleOpenDrawer() {
        SideDrawerHelper.open(drawerOverlay, drawerPanel);
    }

    @FXML private void handleCloseDrawer() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML private void handleGoToHome() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
        loadTimeline();
    }

    @FXML private void handleGoToSearch() {
        NavigationManager.switchScene("/views/Search.fxml");
    }

    @FXML private void handleCreatePost() {
        NavigationManager.switchScene("/views/Compose.fxml");
    }

    @FXML private void handleGoToProfile() {
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML private void handleGoToBookmarks() {
        NavigationManager.switchScene("/views/Bookmarks.fxml");
    }

    @FXML private void handleLogout() {
        LogoutHelper.showConfirmation();
    }
}