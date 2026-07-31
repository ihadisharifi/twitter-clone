package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import shared.models.User;
import shared.models.Tweet;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class BookmarksController {

    @FXML private VBox bookmarksContainer;
    @FXML private HBox drawerOverlay;
    @FXML private VBox drawerPanel;
    @FXML private Label drawerDisplayName;
    @FXML private Label drawerUsername;
    @FXML private Label drawerFollowingCount;
    @FXML private Label drawerFollowersCount;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadBookmarks();
        loadDrawerProfile();
    }

    private void loadBookmarks() {
        Task<List<Tweet>> task = new Task<>() {
            @Override protected List<Tweet> call() throws Exception {
                Response response = send(RequestType.GET_BOOKMARKS, new JsonObject());
                List<Tweet> tweets = new ArrayList<>();
                if (response.getPayload() != null && response.getPayload().isJsonArray()) {
                    for (JsonElement element : response.getPayload().getAsJsonArray())
                        tweets.add(gson.fromJson(element, Tweet.class));
                }
                return tweets;
            }
        };
        task.setOnSucceeded(event -> renderBookmarks(task.getValue()));
        start(task, "load-bookmarks");
    }

    private void renderBookmarks(List<Tweet> tweets) {
        bookmarksContainer.getChildren().clear();
        if (tweets.isEmpty()) {
            Label empty = new Label("Save posts to find them here.");
            empty.setTextFill(Color.web("#71767b"));
            empty.setStyle("-fx-padding: 60 30; -fx-font-size: 16px;");
            bookmarksContainer.getChildren().add(empty);
            return;
        }
        for (Tweet tweet : tweets) {
            VBox card = new VBox(7);
            card.setStyle("-fx-padding: 14 16; -fx-border-color: #333333; -fx-border-width: 0 0 1 0;");
            HBox header = new HBox(8);
            Label author = new Label(authorName(tweet));
            author.setTextFill(Color.WHITE);
            author.setFont(Font.font("System", FontWeight.BOLD, 14));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button remove = new Button("Remove");
            remove.setOnAction(event -> removeBookmark(tweet, remove));
            header.getChildren().addAll(author, spacer, remove);
            Label content = new Label(tweet.getContent() == null ? "" : tweet.getContent());
            content.setTextFill(Color.web("#e7e9ea"));
            content.setWrapText(true);
            card.getChildren().addAll(header, content);
            bookmarksContainer.getChildren().add(card);
        }
    }

    private String authorName(Tweet tweet) {
        User current = UserSession.getInstance().getCurrentUser();
        return current != null && current.getId() == tweet.getAuthorId()
                ? current.getDisplayName() : "User #" + tweet.getAuthorId();
    }

    private void removeBookmark(Tweet tweet, Button button) {
        button.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("tweetId", tweet.getId());
                send(RequestType.UNBOOKMARK_TWEET, body);
                return null;
            }
        };
        task.setOnSucceeded(event -> loadBookmarks());
        task.setOnFailed(event -> button.setDisable(false));
        start(task, "remove-bookmark");
    }

    private Response send(RequestType type, JsonObject body) throws Exception {
        String token = UserSession.getInstance().getToken();
        if (token == null || token.isBlank()) throw new IllegalStateException("Your session has expired.");
        body.addProperty("token", token);
        if (!connection.isConnected()) connection.connect();
        Response response = connection.sendMessage(new Request(UUID.randomUUID().toString(), type, body));
        if (response == null || response.getStatus() != StatusCode.OK)
            throw new IllegalStateException(response == null ? "Request failed." : response.getMessage());
        return response;
    }

    private void loadDrawerProfile() {
        Task<JsonObject> task = new Task<>() {
            @Override
            protected JsonObject call() throws Exception {
                String token = UserSession.getInstance().getToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Your session has expired.");
                }
                if (!connection.isConnected()) {
                    connection.connect();
                }
                JsonObject body = new JsonObject();
                body.addProperty("token", token);
                Response response = connection.sendMessage(new Request(
                        UUID.randomUUID().toString(), RequestType.GET_PROFILE, body
                ));
                if (response == null || response.getStatus() != StatusCode.OK
                        || response.getPayload() == null || !response.getPayload().isJsonObject()) {
                    throw new IllegalStateException("Unable to load profile.");
                }
                return response.getPayload().getAsJsonObject();
            }
        };
        task.setOnSucceeded(event -> {
            JsonObject payload = task.getValue();
            User user = gson.fromJson(payload.get("user"), User.class);
            if (user != null) {
                User current = UserSession.getInstance().getCurrentUser();
                if (current != null) {
                    current.setDisplayName(user.getDisplayName());
                    current.setBio(user.getBio());
                    current.setAvatarUrl(user.getAvatarUrl());
                    current.setBannerUrl(user.getBannerUrl());
                }
                SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
            }
            drawerFollowingCount.setText(number(payload, "followingCount"));
            drawerFollowersCount.setText(number(payload, "followersCount"));
        });
        start(task, "load-bookmarks-drawer-profile");
    }

    private String number(JsonObject payload, String property) {
        return payload.has(property) && !payload.get(property).isJsonNull()
                ? String.valueOf(payload.get(property).getAsInt()) : "0";
    }

    private void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML private void handleOpenDrawer() {
        loadDrawerProfile();
        SideDrawerHelper.open(drawerOverlay, drawerPanel);
    }
    @FXML private void handleCloseDrawer() { SideDrawerHelper.close(drawerOverlay, drawerPanel); }
    @FXML private void handleBack() { NavigationManager.switchScene("/views/Feed.fxml"); }
    @FXML private void handleGoToHome() { NavigationManager.switchScene("/views/Feed.fxml"); }
    @FXML private void handleGoToSearch() { NavigationManager.switchScene("/views/Search.fxml"); }
    @FXML private void handleCreatePost() { NavigationManager.switchScene("/views/Compose.fxml"); }
    @FXML private void handleGoToProfile() { NavigationManager.switchScene("/views/Profile.fxml"); }
    @FXML private void handleGoToBookmarks() { SideDrawerHelper.close(drawerOverlay, drawerPanel); }
    @FXML private void handleLogout() { LogoutHelper.showConfirmation(); }
}
