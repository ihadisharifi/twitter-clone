package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetMediaHelper;
import client.TweetTimeFormatter;
import client.UserSession;
import client.UserAvatarHelper;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ProfileController {

    @FXML private Label nameLabel;
    @FXML private Label usernameLabel;
    @FXML private Label bioLabel;
    @FXML private Label followingCountLabel;
    @FXML private Label followersCountLabel;
    @FXML private Button followButton;
    @FXML private VBox userTweetsContainer;

    @FXML private HBox drawerOverlay;
    @FXML private VBox drawerPanel;
    @FXML private Label drawerDisplayName;
    @FXML private Label drawerUsername;
    @FXML private Label drawerFollowingCount;
    @FXML private Label drawerFollowersCount;

    @FXML private ImageView avatarImageView;
    @FXML private Circle avatarPlaceholder;
    @FXML private Label avatarPlaceholderIcon;
    @FXML private ImageView bannerImageView;
    @FXML private Region bannerPlaceholder;

    private static final String ACTION_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #71767b; -fx-padding: 0; -fx-cursor: hand;";
    private static final String LIKE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #f91880; -fx-padding: 0; -fx-cursor: hand;";
    private static final String DELETE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #f4212e; -fx-padding: 0; -fx-cursor: hand;";

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();
    public static String pendingProfileUsername;

    private User profileUser;
    private boolean ownProfile;
    private boolean following;

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadProfile();
    }

    public void refreshProfileData() {
        loadProfile();
    }

    private void loadProfile() {
        showMessage("Loading profile...", "#71767b");

        Task<ProfileData> task = new Task<>() {
            @Override
            protected ProfileData call() throws Exception {
                String requestedUsername = consumePendingUsername();
                if (requestedUsername == null && profileUser != null) {
                    requestedUsername = profileUser.getUsername();
                }
                JsonObject profileBody = authenticatedBody();
                if (requestedUsername != null) {
                    profileBody.addProperty("username", requestedUsername);
                }
                Response profileResponse = send(RequestType.GET_PROFILE, profileBody);
                JsonObject profilePayload = requireObject(profileResponse.getPayload(), "Invalid profile response.");
                User user = gson.fromJson(profilePayload.get("user"), User.class);
                if (user == null) {
                    throw new IllegalStateException("The server did not return a profile.");
                }

                JsonObject tweetBody = authenticatedBody();
                tweetBody.addProperty("username", user.getUsername());
                Response tweetsResponse = send(RequestType.GET_USER_TWEETS, tweetBody);
                List<Tweet> tweets = parseTweets(tweetsResponse.getPayload());

                boolean follows = false;
                User current = UserSession.getInstance().getCurrentUser();
                if (current != null && current.getId() != user.getId()) {
                    Response followingResponse = send(RequestType.GET_FOLLOWING, authenticatedBody());
                    follows = containsUser(followingResponse.getPayload(), user.getId());
                }

                return new ProfileData(
                        user,
                        tweets,
                        integer(profilePayload, "followingCount"),
                        integer(profilePayload, "followersCount"),
                        follows
                );
            }
        };

        task.setOnSucceeded(event -> renderProfile(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            showError(error == null ? "Unable to load profile." : error.getMessage());
        });
        start(task, "load-profile");
    }

    private void renderProfile(ProfileData data) {
        profileUser = data.user();
        UserSession session = UserSession.getInstance();
        User current = session.getCurrentUser();
        ownProfile = current != null && current.getId() == profileUser.getId();
        following = data.following();
        if (ownProfile) {
            current.setDisplayName(profileUser.getDisplayName());
            current.setBio(profileUser.getBio());
            current.setAvatarUrl(profileUser.getAvatarUrl());
            current.setBannerUrl(profileUser.getBannerUrl());
        }

        nameLabel.setText(displayName(profileUser));
        usernameLabel.setText("@" + safeUsername(profileUser));
        bioLabel.setText(blankToEmpty(profileUser.getBio()));
        followingCountLabel.setText(String.valueOf(data.followingCount()));
        followersCountLabel.setText(String.valueOf(data.followersCount()));
        if (ownProfile) {
            drawerFollowingCount.setText(String.valueOf(data.followingCount()));
            drawerFollowersCount.setText(String.valueOf(data.followersCount()));
        }
        configureFollowButton();
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);

        renderAvatar(profileUser.getAvatarUrl());
        renderBanner(profileUser.getBannerUrl());
        renderTweets(data.tweets());
    }

    private List<Tweet> parseTweets(JsonElement payload) {
        List<Tweet> tweets = new ArrayList<>();
        if (payload != null && payload.isJsonArray()) {
            for (JsonElement element : payload.getAsJsonArray()) {
                Tweet tweet = gson.fromJson(element, Tweet.class);
                if (tweet != null) {
                    tweets.add(tweet);
                }
            }
        }
        tweets.sort(Comparator.comparing(this::createdAt).reversed());
        return tweets;
    }

    private void renderTweets(List<Tweet> tweets) {
        userTweetsContainer.getChildren().clear();
        if (tweets.isEmpty()) {
            showMessage("You haven't posted yet.", "#71767b");
            return;
        }
        for (Tweet tweet : tweets) {
            userTweetsContainer.getChildren().add(createTweetCard(tweet));
        }
    }

    private Node createTweetCard(Tweet tweet) {
        VBox card = new VBox(6);
        card.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0; -fx-padding: 12 16;");

        HBox row = new HBox(12);
        Node avatar = UserAvatarHelper.create(profileUser, 42);

        VBox content = new VBox(5);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox header = new HBox(8);
        Label displayName = label(displayName(profileUser), Color.WHITE, FontWeight.BOLD, 15);
        Label username = label("@" + safeUsername(profileUser), Color.web("#71767b"), FontWeight.NORMAL, 14);
        Label timestamp = label(formatTime(tweet.getCreatedAt()), Color.web("#71767b"), FontWeight.NORMAL, 14);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(displayName, username, timestamp, spacer);
        if (ownProfile) {
            Button delete = new Button("🗑");
            delete.setStyle(DELETE_STYLE);
            delete.setOnAction(event -> deleteTweet(tweet.getId()));
            header.getChildren().add(delete);
        }

        Label body = new Label(blankToEmpty(tweet.getContent()));
        body.setTextFill(Color.web("#e7e9ea"));
        body.setFont(Font.font(15));
        body.setWrapText(true);
        body.setMaxWidth(420);
        content.getChildren().addAll(header, body);

        if (tweet.getMedia() != null && !tweet.getMedia().isEmpty()) {
            Node media = TweetMediaHelper.createMediaNode(tweet.getMedia().get(0).getUrl(), 380, 220);
            if (media != null) {
                content.getChildren().add(media);
            }
        }

        Button like = new Button();
        styleLikeButton(like, tweet);
        like.setOnAction(event -> toggleLike(tweet, like));
        content.getChildren().add(like);

        row.getChildren().addAll(avatar, content);
        card.getChildren().add(row);
        return card;
    }

    private void toggleLike(Tweet tweet, Button button) {
        button.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                JsonObject body = authenticatedBody();
                body.addProperty("tweetId", tweet.getId());
                send(tweet.isLikedByCurrentUser() ? RequestType.UNLIKE_TWEET : RequestType.LIKE_TWEET, body);
                return null;
            }
        };
        task.setOnSucceeded(event -> loadProfile());
        task.setOnFailed(event -> {
            button.setDisable(false);
            showError(message(task.getException(), "Unable to update like."));
        });
        start(task, "toggle-profile-like");
    }

    private void deleteTweet(int tweetId) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                JsonObject body = authenticatedBody();
                body.addProperty("tweetId", tweetId);
                send(RequestType.DELETE_TWEET, body);
                return null;
            }
        };
        task.setOnSucceeded(event -> loadProfile());
        task.setOnFailed(event -> showError(message(task.getException(), "Unable to delete post.")));
        start(task, "delete-profile-tweet");
    }

    @FXML
    private void handleAvatarClick() {
        if (ownProfile) {
            chooseAndSaveImage(true);
        }
    }

    @FXML
    private void handleBannerClick() {
        if (ownProfile) {
            chooseAndSaveImage(false);
        }
    }

    @FXML
    private void handleFollowToggle() {
        if (ownProfile || profileUser == null || followButton == null) {
            return;
        }
        followButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                JsonObject body = authenticatedBody();
                body.addProperty("userId", profileUser.getId());
                send(following ? RequestType.UNFOLLOW : RequestType.FOLLOW, body);
                return null;
            }
        };
        task.setOnSucceeded(event -> loadProfileFor(profileUser.getUsername()));
        task.setOnFailed(event -> {
            followButton.setDisable(false);
            showError(message(task.getException(), "Unable to update follow status."));
        });
        start(task, "toggle-profile-follow");
    }

    private void chooseAndSaveImage(boolean avatar) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(avatar ? "Select profile photo" : "Select header photo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif")
        );
        Stage stage = (Stage) nameLabel.getScene().getWindow();
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            updateProfileImage(avatar, selected.toURI().toString());
        }
    }

    private void updateProfileImage(boolean avatar, String url) {
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                JsonObject body = authenticatedBody();
                body.addProperty(avatar ? "avatarUrl" : "bannerUrl", url);
                Response response = send(RequestType.UPDATE_PROFILE, body);
                return gson.fromJson(response.getPayload(), User.class);
            }
        };
        task.setOnSucceeded(event -> loadProfile());
        task.setOnFailed(event -> showError(message(task.getException(), "Unable to update profile image.")));
        start(task, "update-profile-image");
    }

    @FXML
    private void handleShowFollowing() {
        loadConnections(RequestType.GET_FOLLOWING, "Following");
    }

    @FXML
    private void handleShowFollowers() {
        loadConnections(RequestType.GET_FOLLOWERS, "Followers");
    }

    private void loadConnections(RequestType type, String title) {
        if (profileUser == null) {
            return;
        }
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                JsonObject body = authenticatedBody();
                body.addProperty("userId", profileUser.getId());
                Response response = send(type, body);
                User[] users = gson.fromJson(response.getPayload(), User[].class);
                return users == null ? List.of() : List.of(users);
            }
        };
        task.setOnSucceeded(event -> showConnections(title, task.getValue()));
        task.setOnFailed(event -> showError(message(task.getException(), "Unable to load users.")));
        start(task, "load-profile-connections");
    }

    private void showConnections(String title, List<User> users) {
        VBox list = new VBox(2);
        if (users.isEmpty()) {
            Label empty = new Label("No users yet.");
            empty.setTextFill(Color.web("#71767b"));
            empty.setStyle("-fx-padding: 24;");
            list.getChildren().add(empty);
        } else {
            for (User user : users) {
                HBox row = new HBox(12);
                row.setStyle("-fx-padding: 10; -fx-cursor: hand;");
                VBox identity = new VBox(2);
                Label name = label(displayName(user), Color.WHITE, FontWeight.BOLD, 15);
                Label handle = label("@" + safeUsername(user), Color.web("#71767b"), FontWeight.NORMAL, 14);
                identity.getChildren().addAll(name, handle);
                row.getChildren().addAll(UserAvatarHelper.create(user, 42), identity);
                row.setOnMouseClicked(event -> {
                    loadProfileFor(user.getUsername());
                    ((Stage) row.getScene().getWindow()).close();
                });
                list.getChildren().add(row);
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        Alert dialog = new Alert(Alert.AlertType.NONE, "", ButtonType.CLOSE);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(scroll);
        pane.setStyle("-fx-background-color: #000000;");
        dialog.show();
    }

    private Response send(RequestType type, JsonObject payload) throws Exception {
        if (!connection.isConnected()) {
            connection.connect();
        }
        Request request = new Request(UUID.randomUUID().toString(), type, payload);
        Response response = connection.sendMessage(request);
        if (response == null) {
            throw new IllegalStateException("The server returned no response.");
        }
        if (response.getStatus() != StatusCode.OK) {
            throw new IllegalStateException(
                    response.getMessage() == null ? "The server rejected the request." : response.getMessage()
            );
        }
        return response;
    }

    private JsonObject authenticatedBody() {
        String token = UserSession.getInstance().getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Your session has expired. Please sign in again.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        return body;
    }

    private void renderAvatar(String url) {
        Image image = loadImage(url, 180, 180);
        boolean hasImage = image != null;
        avatarImageView.setImage(image);
        avatarImageView.setVisible(hasImage);
        avatarImageView.setManaged(hasImage);
        avatarPlaceholder.setVisible(!hasImage);
        avatarPlaceholderIcon.setVisible(!hasImage);
        if (hasImage) {
            avatarImageView.setClip(new Circle(45, 45, 45));
        }
    }

    private void renderBanner(String url) {
        Image image = loadImage(url, 1200, 300);
        boolean hasImage = image != null;
        bannerImageView.setImage(image);
        bannerImageView.setVisible(hasImage);
        bannerImageView.setManaged(hasImage);
        bannerPlaceholder.setVisible(!hasImage);
    }

    private Image loadImage(String url, double width, double height) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            Image image = new Image(url, width, height, false, true);
            return image.isError() ? null : image;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void styleLikeButton(Button button, Tweet tweet) {
        button.setText((tweet.isLikedByCurrentUser() ? "❤ " : "♡ ") + tweet.getLikesCount());
        button.setStyle(tweet.isLikedByCurrentUser() ? LIKE_STYLE : ACTION_STYLE);
    }

    private Label label(String text, Color color, FontWeight weight, double size) {
        Label label = new Label(text);
        label.setTextFill(color);
        label.setFont(Font.font("System", weight, size));
        return label;
    }

    private JsonObject requireObject(JsonElement payload, String error) {
        if (payload == null || !payload.isJsonObject()) {
            throw new IllegalStateException(error);
        }
        return payload.getAsJsonObject();
    }

    private int integer(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsInt() : 0;
    }

    private boolean containsUser(JsonElement payload, int userId) {
        if (payload == null || !payload.isJsonArray()) {
            return false;
        }
        for (JsonElement element : payload.getAsJsonArray()) {
            User user = gson.fromJson(element, User.class);
            if (user != null && user.getId() == userId) {
                return true;
            }
        }
        return false;
    }

    private synchronized String consumePendingUsername() {
        String username = pendingProfileUsername;
        pendingProfileUsername = null;
        return username;
    }

    private void loadProfileFor(String username) {
        pendingProfileUsername = username;
        loadProfile();
    }

    private void configureFollowButton() {
        if (followButton == null) {
            return;
        }
        followButton.setVisible(!ownProfile);
        followButton.setManaged(!ownProfile);
        followButton.setDisable(false);
        followButton.setText(following ? "Following" : "Follow");
        followButton.setStyle(following
                ? "-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: #536471; "
                + "-fx-border-radius: 18; -fx-background-radius: 18; -fx-font-weight: bold; -fx-padding: 6 18;"
                : "-fx-background-color: white; -fx-text-fill: black; -fx-background-radius: 18; "
                + "-fx-font-weight: bold; -fx-padding: 6 18;");
    }

    private Instant createdAt(Tweet tweet) {
        try {
            return Instant.parse(tweet.getCreatedAt());
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private String formatTime(String value) {
        try {
            return TweetTimeFormatter.formatFeedDot(Instant.parse(value));
        } catch (DateTimeParseException | NullPointerException exception) {
            return "";
        }
    }

    private String displayName(User user) {
        String name = user == null ? null : user.getDisplayName();
        return name == null || name.isBlank() ? safeUsername(user) : name;
    }

    private String safeUsername(User user) {
        String username = user == null ? null : user.getUsername();
        return username == null || username.isBlank() ? "user" : username;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null ? fallback : error.getMessage();
    }

    private void showMessage(String text, String color) {
        userTweetsContainer.getChildren().clear();
        Label label = new Label(text);
        label.setTextFill(Color.web(color));
        label.setStyle("-fx-padding: 40 16; -fx-font-size: 15px;");
        userTweetsContainer.getChildren().add(label);
    }

    private void showError(String text) {
        showMessage(text, "#f4212e");
    }

    private void start(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML private void handleOpenDrawer() { SideDrawerHelper.open(drawerOverlay, drawerPanel); }
    @FXML private void handleCloseDrawer() { SideDrawerHelper.close(drawerOverlay, drawerPanel); }
    @FXML private void handleGoToHome() { NavigationManager.switchScene("/views/Feed.fxml"); }
    @FXML private void handleGoToSearch() { NavigationManager.switchScene("/views/Search.fxml"); }
    @FXML private void handleCreatePost() { NavigationManager.switchScene("/views/Compose.fxml"); }
    @FXML private void handleGoToProfile() {
        User current = UserSession.getInstance().getCurrentUser();
        if (current != null && !ownProfile) {
            loadProfileFor(current.getUsername());
        } else {
            SideDrawerHelper.close(drawerOverlay, drawerPanel);
        }
    }
    @FXML private void handleGoToBookmarks() { NavigationManager.switchScene("/views/Bookmarks.fxml"); }
    @FXML private void handleLogout() { LogoutHelper.showConfirmation(); }

    private record ProfileData(
            User user,
            List<Tweet> tweets,
            int followingCount,
            int followersCount,
            boolean following
    ) {}
}
