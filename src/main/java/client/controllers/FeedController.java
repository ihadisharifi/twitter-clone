package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetMediaHelper;
import client.TweetTimeFormatter;
import client.UserSession;
import client.UserAvatarHelper;
import client.UiIconHelper;
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
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
    @FXML private Label drawerFollowingCount;
    @FXML private Label drawerFollowersCount;
    @FXML private ImageView drawerAvatarImage;
    @FXML private Circle drawerAvatarPlaceholder;
    @FXML private Label drawerAvatarPlaceholderIcon;
    @FXML private ImageView topAvatarImage;
    @FXML private Circle topAvatarPlaceholder;
    @FXML private Label topAvatarPlaceholderIcon;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadDrawerProfile();
        loadTimeline();
    }

    private void loadDrawerProfile() {
        Task<JsonObject> task = new Task<>() {
            @Override
            protected JsonObject call() throws Exception {
                JsonObject body = new JsonObject();
                String token = UserSession.getInstance().getToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Your session has expired.");
                }
                body.addProperty("token", token);

                if (!connection.isConnected()) {
                    connection.connect();
                }
                Response response = connection.sendMessage(new Request(
                        UUID.randomUUID().toString(),
                        RequestType.GET_PROFILE,
                        body
                ));
                if (response == null || response.getStatus() != StatusCode.OK
                        || response.getPayload() == null || !response.getPayload().isJsonObject()) {
                    throw new IllegalStateException(
                            response == null || response.getMessage() == null
                                    ? "Unable to load profile."
                                    : response.getMessage()
                    );
                }
                return response.getPayload().getAsJsonObject();
            }
        };

        task.setOnSucceeded(event -> renderDrawerProfile(task.getValue()));
        Thread thread = new Thread(task, "load-home-drawer-profile");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderDrawerProfile(JsonObject payload) {
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
            renderAvatar(user.getAvatarUrl(), drawerAvatarImage, drawerAvatarPlaceholder,
                    drawerAvatarPlaceholderIcon, 48);
            renderAvatar(user.getAvatarUrl(), topAvatarImage, topAvatarPlaceholder,
                    topAvatarPlaceholderIcon, 32);
        }
        drawerFollowingCount.setText(jsonInteger(payload, "followingCount"));
        drawerFollowersCount.setText(jsonInteger(payload, "followersCount"));
    }

    private String jsonInteger(JsonObject object, String property) {
        return object.has(property) && !object.get(property).isJsonNull()
                ? String.valueOf(object.get(property).getAsInt())
                : "0";
    }

    private void renderAvatar(
            String url,
            ImageView imageView,
            Circle placeholder,
            Label placeholderIcon,
            double size
    ) {
        Image image = null;
        if (url != null && !url.isBlank()) {
            try {
                Image candidate = new Image(url, size * 2, size * 2, false, true);
                if (!candidate.isError()) {
                    image = candidate;
                }
            } catch (RuntimeException ignored) {
            }
        }

        boolean available = image != null;
        imageView.setImage(image);
        imageView.setVisible(available);
        imageView.setManaged(available);
        placeholder.setVisible(!available);
        placeholderIcon.setVisible(!available);
        if (available) {
            imageView.setClip(new Circle(size / 2, size / 2, size / 2));
        }
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
            feedType = RequestType.GET_FEED;
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

        FeedData feed = parseFeedPayload(response.getPayload());

        JsonObject followingBody = new JsonObject();
        followingBody.addProperty("token", token);
        Response followingResponse = connection.sendMessage(new Request(
                UUID.randomUUID().toString(),
                RequestType.GET_FOLLOWING,
                followingBody
        ));
        if (followingResponse != null && followingResponse.getStatus() == StatusCode.OK
                && followingResponse.getPayload() != null
                && followingResponse.getPayload().isJsonArray()) {
            User[] followingUsers = gson.fromJson(followingResponse.getPayload(), User[].class);
            if (followingUsers != null) {
                for (User user : followingUsers) {
                    feed.usersById().put(user.getId(), user);
                }
            }
        }
        return feed;
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

        Map<Integer, Tweet> tweetsById = new HashMap<>();
        Map<Integer, List<Tweet>> repliesByParent = new HashMap<>();
        Map<Integer, List<Tweet>> repostsByOriginal = new HashMap<>();
        Map<Integer, Tweet> currentUserReposts = new HashMap<>();
        User currentUser = UserSession.getInstance().getCurrentUser();

        for (Tweet tweet : feed.tweets()) {
            tweetsById.put(tweet.getId(), tweet);
            if (tweet.getReplyToId() != null) {
                repliesByParent.computeIfAbsent(tweet.getReplyToId(), ignored -> new ArrayList<>()).add(tweet);
            }
            if (tweet.getRetweetToId() != null) {
                repostsByOriginal.computeIfAbsent(tweet.getRetweetToId(), ignored -> new ArrayList<>()).add(tweet);
                if (currentUser != null && tweet.getAuthorId() == currentUser.getId()) {
                    currentUserReposts.put(tweet.getRetweetToId(), tweet);
                }
            }
        }

        List<Tweet> timelineTweets = feed.tweets().stream()
                .filter(tweet -> tweet.getReplyToId() == null)
                .sorted(Comparator.comparing(this::createdAtOrEpoch).reversed())
                .toList();

        if (timelineTweets.isEmpty()) {
            showEmptyState();
            return;
        }

        for (Tweet tweet : timelineTweets) {
            timelineContainer.getChildren().add(createTweetCard(
                    tweet, feed.usersById(), tweetsById, repliesByParent,
                    repostsByOriginal, currentUserReposts
            ));
        }
    }

    private Node createTweetCard(
            Tweet timelineTweet,
            Map<Integer, User> usersById,
            Map<Integer, Tweet> tweetsById,
            Map<Integer, List<Tweet>> repliesByParent,
            Map<Integer, List<Tweet>> repostsByOriginal,
            Map<Integer, Tweet> currentUserReposts
    ) {
        Tweet tweet = timelineTweet.getRetweetToId() == null
                ? timelineTweet
                : tweetsById.getOrDefault(timelineTweet.getRetweetToId(), timelineTweet);
        User author = usersById.get(tweet.getAuthorId());

        VBox card = new VBox(6);
        card.setStyle(
                "-fx-border-color: #333333; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-padding: 12 16;"
        );

        if (timelineTweet.getRetweetToId() != null) {
            User reposter = usersById.get(timelineTweet.getAuthorId());
            Label reposted = new Label(displayName(reposter, timelineTweet) + " reposted");
            reposted.setTextFill(Color.web("#71767b"));
            reposted.setFont(Font.font("System", 13));
            reposted.setStyle("-fx-padding: 0 0 0 54;");
            reposted.setGraphic(UiIconHelper.create(UiIconHelper.Icon.REPOST, "#71767b", 0.62));
            card.getChildren().add(reposted);
        }

        HBox row = new HBox(12);

        User avatarUser = author;
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (avatarUser == null && currentUser != null && currentUser.getId() == tweet.getAuthorId()) {
            avatarUser = currentUser;
        }
        Node avatar = UserAvatarHelper.create(avatarUser, 42);

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
        if (currentUser != null && timelineTweet.getAuthorId() == currentUser.getId()) {
            Region headerSpacer = new Region();
            HBox.setHgrow(headerSpacer, Priority.ALWAYS);
            Button deleteButton = actionButton("🗑");
            deleteButton.setStyle(actionStyle("#f4212e"));
            deleteButton.setOnAction(event -> deleteOwnedTweet(timelineTweet, deleteButton));
            header.getChildren().addAll(headerSpacer, deleteButton);
        }
        if (author != null) {
            header.setStyle("-fx-cursor: hand;");
            header.setOnMouseClicked(event -> openProfile(author));
            avatar.setOnMouseClicked(event -> openProfile(author));
        }

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

        HBox actions = new HBox(36);
        actions.setStyle("-fx-padding: 6 0 0 0;");

        VBox replyComposer = createReplyComposer(tweet);
        List<Tweet> replies = new ArrayList<>(
                repliesByParent.getOrDefault(tweet.getId(), List.of())
        );
        replies.sort(Comparator.comparing(this::createdAtOrEpoch));

        VBox replySection = new VBox(6);
        replySection.setVisible(false);
        replySection.setManaged(false);
        replySection.getChildren().add(replyComposer);
        replyComposer.setVisible(true);
        replyComposer.setManaged(true);

        if (!replies.isEmpty()) {
            VBox thread = new VBox(0);
            thread.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: #2f3336; -fx-border-width: 0 0 0 2;");
            for (Tweet reply : replies) {
                thread.getChildren().add(createReplyNode(reply, usersById));
            }
            replySection.getChildren().add(thread);
        }

        Button replyButton = actionButton("💬 " + replies.size());
        replyButton.setOnAction(event -> {
            boolean show = !replySection.isVisible();
            replySection.setVisible(show);
            replySection.setManaged(show);
        });

        List<Tweet> reposts = repostsByOriginal.getOrDefault(tweet.getId(), List.of());
        Tweet ownRepost = currentUserReposts.get(tweet.getId());
        Button retweetButton = actionButton("🔁 " + reposts.size());
        if (ownRepost != null) {
            retweetButton.setStyle(actionStyle("#00ba7c"));
            UiIconHelper.apply(retweetButton, UiIconHelper.Icon.REPOST, "#00ba7c");
        }
        retweetButton.setOnAction(event -> toggleRepost(tweet, ownRepost, retweetButton));

        Button likeButton = actionButton(
                (tweet.isLikedByCurrentUser() ? "❤ " : "♡ ") + tweet.getLikesCount()
        );
        if (tweet.isLikedByCurrentUser()) {
            likeButton.setStyle(actionStyle("#f91880"));
            UiIconHelper.apply(likeButton, UiIconHelper.Icon.HEART, "#f91880");
        }
        likeButton.setOnAction(event -> toggleLike(tweet, likeButton));
        actions.getChildren().addAll(replyButton, retweetButton, likeButton);
        content.getChildren().addAll(actions, replySection);

        row.getChildren().addAll(avatar, content);
        card.getChildren().add(row);
        return card;
    }

    private Node createReplyNode(Tweet reply, Map<Integer, User> usersById) {
        User author = usersById.get(reply.getAuthorId());
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 10 0 10 12;");
        Node avatar = UserAvatarHelper.create(author, 34);
        VBox content = new VBox(4);
        HBox header = new HBox(7);
        Label name = new Label(displayName(author, reply));
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("System", FontWeight.BOLD, 14));
        Label handle = new Label("@" + username(author, reply));
        handle.setTextFill(Color.web("#71767b"));
        header.getChildren().addAll(name, handle);
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null && reply.getAuthorId() == currentUser.getId()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button delete = actionButton("🗑");
            delete.setStyle(actionStyle("#f4212e"));
            delete.setOnAction(event -> deleteOwnedTweet(reply, delete));
            header.getChildren().addAll(spacer, delete);
        }
        if (author != null) {
            header.setOnMouseClicked(event -> openProfile(author));
            header.setStyle("-fx-cursor: hand;");
            avatar.setOnMouseClicked(event -> openProfile(author));
        }
        Label text = new Label(reply.getContent() == null ? "" : reply.getContent());
        text.setTextFill(Color.web("#e7e9ea"));
        text.setWrapText(true);
        content.getChildren().addAll(header, text);
        if (reply.getMedia() != null && !reply.getMedia().isEmpty()) {
            Node media = TweetMediaHelper.createMediaNode(reply.getMedia().get(0).getUrl(), 330, 180);
            if (media != null) content.getChildren().add(media);
        }
        row.getChildren().addAll(avatar, content);
        return row;
    }

    private VBox createReplyComposer(Tweet parent) {
        VBox composer = new VBox(8);
        composer.setVisible(false);
        composer.setManaged(false);
        composer.setStyle("-fx-padding: 10; -fx-background-color: #16181c; -fx-background-radius: 10;");

        TextArea input = new TextArea();
        input.setPromptText("Post your reply");
        input.setPrefRowCount(3);
        input.setWrapText(true);
        input.setStyle("-fx-control-inner-background: #000000; -fx-text-fill: white; "
                + "-fx-prompt-text-fill: #71767b; -fx-border-color: #333333;");

        StackPane mediaPreview = new StackPane();
        mediaPreview.setVisible(false);
        mediaPreview.setManaged(false);
        final String[] selectedMedia = new String[1];

        HBox controls = new HBox(10);
        Button attach = actionButton("🖼");
        Button remove = actionButton("✕");
        remove.setVisible(false);
        remove.setManaged(false);
        Button post = new Button("Reply");
        post.setStyle("-fx-background-color: #1d9bf0; -fx-text-fill: white; "
                + "-fx-background-radius: 18; -fx-font-weight: bold; -fx-cursor: hand;");

        attach.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Attach media to reply");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Media", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.mp4", "*.m4v"
                    )
            );
            File file = chooser.showOpenDialog((Stage) timelineContainer.getScene().getWindow());
            if (file != null) {
                selectedMedia[0] = file.toURI().toString();
                Node preview = TweetMediaHelper.createMediaNode(selectedMedia[0], 330, 170);
                if (preview != null) {
                    mediaPreview.getChildren().setAll(preview);
                    mediaPreview.setVisible(true);
                    mediaPreview.setManaged(true);
                    remove.setVisible(true);
                    remove.setManaged(true);
                }
            }
        });
        remove.setOnAction(event -> {
            selectedMedia[0] = null;
            mediaPreview.getChildren().clear();
            mediaPreview.setVisible(false);
            mediaPreview.setManaged(false);
            remove.setVisible(false);
            remove.setManaged(false);
        });
        post.setOnAction(event -> {
            String text = input.getText() == null ? "" : input.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            post.setDisable(true);
            Task<Void> task = requestTask(RequestType.CREATE_TWEET, body -> {
                body.addProperty("content", text);
                body.addProperty("replyToId", parent.getId());
                if (selectedMedia[0] != null) {
                    JsonArray media = new JsonArray();
                    media.add(selectedMedia[0]);
                    body.add("mediaUrls", media);
                }
            });
            task.setOnSucceeded(success -> loadTimeline());
            task.setOnFailed(failure -> post.setDisable(false));
            startTask(task, "reply-to-feed-tweet");
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        controls.getChildren().addAll(attach, remove, spacer, post);
        composer.getChildren().addAll(input, mediaPreview, controls);
        return composer;
    }

    private Button actionButton(String text) {
        Button button = new Button();
        String portableText = text;
        UiIconHelper.Icon icon = null;
        String color = "#71767b";
        if (text.startsWith("💬")) {
            icon = UiIconHelper.Icon.REPLY;
            portableText = text.substring("💬".length()).trim();
        } else if (text.startsWith("🔁")) {
            icon = UiIconHelper.Icon.REPOST;
            portableText = text.substring("🔁".length()).trim();
        } else if (text.startsWith("❤") || text.startsWith("♡")) {
            icon = UiIconHelper.Icon.HEART;
            portableText = text.substring(1).trim();
        } else if (text.startsWith("🗑")) {
            icon = UiIconHelper.Icon.TRASH;
            portableText = "Delete";
            color = "#f4212e";
        } else if (text.startsWith("🖼")) {
            icon = UiIconHelper.Icon.IMAGE;
            portableText = "";
            color = "#1d9bf0";
        }
        button.setText(portableText);
        button.setStyle(actionStyle("#71767b"));
        if (icon != null) {
            UiIconHelper.apply(button, icon, color);
        }
        return button;
    }

    private String actionStyle(String color) {
        return "-fx-background-color: transparent; -fx-text-fill: " + color
                + "; -fx-padding: 2 0; -fx-cursor: hand;";
    }

    private void openProfile(User user) {
        ProfileController.pendingProfileUsername = user.getUsername();
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    private void toggleLike(Tweet tweet, Button button) {
        button.setDisable(true);
        Task<Void> task = requestTask(
                tweet.isLikedByCurrentUser() ? RequestType.UNLIKE_TWEET : RequestType.LIKE_TWEET,
                body -> body.addProperty("tweetId", tweet.getId())
        );
        task.setOnSucceeded(event -> loadTimeline());
        task.setOnFailed(event -> button.setDisable(false));
        startTask(task, "toggle-feed-like");
    }

    private void deleteOwnedTweet(Tweet tweet, Button button) {
        User current = UserSession.getInstance().getCurrentUser();
        if (current == null || tweet.getAuthorId() != current.getId()) {
            return;
        }
        button.setDisable(true);
        Task<Void> task = requestTask(
                RequestType.DELETE_TWEET,
                body -> body.addProperty("tweetId", tweet.getId())
        );
        task.setOnSucceeded(event -> loadTimeline());
        task.setOnFailed(event -> button.setDisable(false));
        startTask(task, "delete-owned-feed-tweet");
    }

    private void toggleRepost(Tweet tweet, Tweet ownRepost, Button button) {
        button.setDisable(true);
        Task<Void> task = requestTask(
                ownRepost == null ? RequestType.CREATE_TWEET : RequestType.DELETE_TWEET,
                body -> {
                    if (ownRepost == null) {
                        String content = tweet.getContent();
                        body.addProperty("content",
                                content == null || content.isBlank() ? "Reposted a tweet" : content);
                        body.addProperty("retweetToId", tweet.getId());
                    } else {
                        body.addProperty("tweetId", ownRepost.getId());
                    }
                }
        );
        task.setOnSucceeded(event -> loadTimeline());
        task.setOnFailed(event -> button.setDisable(false));
        startTask(task, ownRepost == null ? "repost-feed-tweet" : "undo-feed-repost");
    }

    private Task<Void> requestTask(RequestType type, java.util.function.Consumer<JsonObject> payloadWriter) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                String token = UserSession.getInstance().getToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Your session has expired.");
                }
                JsonObject body = new JsonObject();
                body.addProperty("token", token);
                payloadWriter.accept(body);
                if (!connection.isConnected()) {
                    connection.connect();
                }
                Response response = connection.sendMessage(new Request(
                        UUID.randomUUID().toString(), type, body
                ));
                if (response == null || response.getStatus() != StatusCode.OK) {
                    throw new IllegalStateException(
                            response == null || response.getMessage() == null
                                    ? "Request failed." : response.getMessage()
                    );
                }
                return null;
            }
        };
    }

    private void startTask(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
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
        loadDrawerProfile();
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
