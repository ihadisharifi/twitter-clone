package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetStore;
import client.TweetStore.StoredTweet;
import client.TweetTimeFormatter;
import client.UserSession;
import client.TweetMediaHelper;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProfileController {
    @FXML
    private Label nameLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private VBox userTweetsContainer;

    @FXML
    private Label bioLabel;

    @FXML
    private HBox drawerOverlay;

    @FXML
    private VBox drawerPanel;

    @FXML
    private Label drawerDisplayName;

    @FXML
    private Label drawerUsername;

    @FXML
    private ImageView avatarImageView;

    @FXML
    private Circle avatarPlaceholder;

    @FXML
    private Label avatarPlaceholderIcon;

    @FXML
    private ImageView bannerImageView;

    @FXML
    private Region bannerPlaceholder;

    @FXML
    private StackPane bannerContainer;

    private final List<TimestampLabel> liveTimestamps = new ArrayList<>();
    private Timeline timeRefreshTimeline;

    private static final String STYLE_ACTION_IDLE =
            "-fx-background-color: transparent; -fx-text-fill: #71767b; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_REPLY_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_RETWEET_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #00ba7c; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_LIKE_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #f91880; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_BOOKMARK_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_COMPOSE_AREA =
            "-fx-control-inner-background: #000000; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #71767b; -fx-border-color: #333333; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 14px;";
    private static final String STYLE_POST_BTN =
            "-fx-background-color: #1d9bf0; -fx-text-fill: #ffffff; -fx-background-radius: 18; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 4 14; -fx-cursor: hand;";
    private static final String STYLE_DELETE_BTN =
            "-fx-background-color: transparent; -fx-text-fill: #f4212e; -fx-padding: 0; -fx-cursor: hand; -fx-font-size: 14;";

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        refreshProfileData();
        startTimestampRefresh();
    }

    private void startTimestampRefresh() {
        if (timeRefreshTimeline != null) {
            timeRefreshTimeline.stop();
        }
        timeRefreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(15), e -> {
                    for (TimestampLabel entry : liveTimestamps) {
                        entry.label.setText(TweetTimeFormatter.formatFeedDot(entry.createdAt));
                    }
                })
        );
        timeRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        timeRefreshTimeline.play();
    }

    private Label createTimestampLabel(Instant createdAt) {
        Label timestamp = new Label(TweetTimeFormatter.formatFeedDot(createdAt));
        timestamp.setFont(Font.font("System", 14));
        timestamp.setTextFill(Color.web("#71767b"));
        Tooltip.install(timestamp, new Tooltip(TweetTimeFormatter.formatAbsolute(createdAt)));
        liveTimestamps.add(new TimestampLabel(timestamp, createdAt));
        return timestamp;
    }

    private static final class TimestampLabel {
        final Label label;
        final Instant createdAt;

        TimestampLabel(Label label, Instant createdAt) {
            this.label = label;
            this.createdAt = createdAt;
        }
    }

    private void loadUserOwnTweets() {
        String username = UserSession.getInstance().getUsername();
        if (username == null) {
            username = "developer";
        }

        TweetStore.getInstance().seedIfEmpty();
        List<StoredTweet> personalPosts = TweetStore.getInstance().getTweetsByUsername(username);

        for (StoredTweet tweet : personalPosts) {
            if (username.equals(tweet.getAuthorUsername())) {
                renderPersonalTweetCard(tweet);
            }
        }
    }

    private void renderPersonalTweetCard(StoredTweet tweet) {
        VBox card = new VBox(4);
        card.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0; -fx-padding: 12 16 12 16;");

        if (tweet.isRetweet()) {
            Label repostLabel = new Label("🔁 You reposted");
            repostLabel.setTextFill(Color.web("#71767b"));
            repostLabel.setFont(Font.font("System", 13));
            card.getChildren().add(repostLabel);
        }

        HBox tweetRow = new HBox(12);

        VBox avatarBox = new VBox();
        Label avatar = new Label("👤");
        avatar.setFont(Font.font("System", 24));
        avatar.setTextFill(Color.web("#71767b"));
        avatarBox.getChildren().add(avatar);

        VBox contentStack = new VBox(4);
        HBox headerRow = new HBox(8);

        // Retweet cards show original author in the main header
        String showName;
        String showHandle;
        if (tweet.isRetweet()) {
            showName = tweet.getOriginalAuthorDisplayName() != null
                    ? tweet.getOriginalAuthorDisplayName()
                    : "User";
            showHandle = tweet.getOriginalAuthorUsername() != null
                    ? tweet.getOriginalAuthorUsername()
                    : "user";
        }
        else {
            showName = tweet.getAuthorDisplayName() != null ? tweet.getAuthorDisplayName() : "Active User";
            showHandle = tweet.getAuthorUsername() != null ? tweet.getAuthorUsername() : "user";
        }

        Label displayName = new Label(showName);
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));
        displayName.setTextFill(Color.WHITE);

        Label userHandle = new Label("@" + showHandle);
        userHandle.setFont(Font.font("System", 14));
        userHandle.setTextFill(Color.web("#71767b"));

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        StoredTweet engagementTarget = engagementTarget(tweet);

        // Header: Delete button ONLY for self-authored posts (not retweets)
        if (!tweet.isRetweet()) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button deleteButton = new Button("🗑");
            deleteButton.setStyle(STYLE_DELETE_BTN);
            deleteButton.setOnAction(event -> {
                String username = currentUsername();
                if (TweetStore.getInstance().deleteTweet(tweet.getId(), username)) {
                    refreshProfileData();
                }
            });
            headerRow.getChildren().addAll(spacer, deleteButton);
        }

        Label bodyText = new Label(tweet.getContent());
        bodyText.setFont(Font.font("System", 15));
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setWrapText(true);
        bodyText.setMaxWidth(420);

        contentStack.getChildren().add(headerRow);

        if (tweet.isReply()) {
            String replyTo = tweet.getOriginalAuthorUsername() != null
                    ? "@" + tweet.getOriginalAuthorUsername()
                    : "someone";
            Label replyHeaderLabel = new Label("Replying to " + replyTo);
            replyHeaderLabel.setTextFill(Color.web("#1d9bf0"));
            replyHeaderLabel.setFont(Font.font("System", 13));
            replyHeaderLabel.setStyle("-fx-padding: 2 0 4 0;");

            VBox parentRefBox = createParentTweetRefBox(tweet);

            contentStack.getChildren().addAll(replyHeaderLabel, parentRefBox);
        }

        contentStack.getChildren().add(bodyText);

        HBox actionToolbar = new HBox(40);
        actionToolbar.setStyle("-fx-padding: 6 0 0 0;");

        // Reply panel (composer + thread) toggled by reply button
        VBox replyPanel = new VBox(8);
        replyPanel.setVisible(false);
        replyPanel.setManaged(false);
        replyPanel.setStyle("-fx-padding: 8 0 0 0;");

        // Mention/Reply button (💬) - present on ALL profile cards
        Button replyButton = new Button();
        applyReplyStyle(replyButton, engagementTarget);
        replyButton.setOnAction(event -> {
            boolean open = !replyPanel.isVisible();
            replyPanel.setVisible(open);
            replyPanel.setManaged(open);
            if (open) {
                rebuildReplyPanel(replyPanel, engagementTarget, replyButton);
            }
        });

        Button likeButton = new Button();
        applyLikeStyle(likeButton, engagementTarget);
        likeButton.setOnAction(event -> {
            TweetStore.getInstance().toggleLike(engagementTarget.getId());
            applyLikeStyle(likeButton, engagementTarget);
        });

        Button bookmarkButton = new Button();
        applyBookmarkStyle(bookmarkButton, engagementTarget);
        bookmarkButton.setOnAction(event -> {
            TweetStore.getInstance().toggleBookmark(engagementTarget.getId());
            applyBookmarkStyle(bookmarkButton, engagementTarget);
        });

        if (!tweet.isRetweet()) {
            // Self-authored: Mention/Reply, Like, Bookmark (No Retweet button)
            actionToolbar.getChildren().addAll(replyButton, likeButton, bookmarkButton);
        } else {
            // Retweeted post: Mention/Reply, Retweet, Like, Bookmark
            Button retweetButton = new Button();
            applyRetweetStyle(retweetButton, engagementTarget);
            retweetButton.setOnAction(event -> {
                TweetStore.getInstance().toggleRetweet(
                        engagementTarget.getId(),
                        currentUsername(),
                        currentDisplayName()
                );
                refreshProfileData();
            });
            actionToolbar.getChildren().addAll(replyButton, retweetButton, likeButton, bookmarkButton);
        }

        renderTweetMediaIfPresent(contentStack, tweet);
        contentStack.getChildren().addAll(actionToolbar, replyPanel);
        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);

        userTweetsContainer.getChildren().add(card);
    }

    private VBox createParentTweetRefBox(StoredTweet tweet) {
        VBox refBox = new VBox(4);
        refBox.setMaxWidth(420);
        refBox.setStyle(
                "-fx-background-color: #16181c; " +
                "-fx-border-color: #333333; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 8 12 8 12; " +
                "-fx-cursor: hand;"
        );

        StoredTweet parentTweet = tweet.getReplyToId() != null
                ? TweetStore.getInstance().findById(tweet.getReplyToId())
                : null;

        String authorName = parentTweet != null ? parentTweet.getAuthorDisplayName() : tweet.getOriginalAuthorDisplayName();
        String authorHandle = parentTweet != null ? parentTweet.getAuthorUsername() : tweet.getOriginalAuthorUsername();
        String parentContent = parentTweet != null ? parentTweet.getContent() : "Original post context";
        String parentMedia = parentTweet != null ? parentTweet.getMediaPath() : null;

        if (authorName == null || authorName.isBlank()) authorName = "User";
        if (authorHandle == null || authorHandle.isBlank()) authorHandle = "user";

        HBox authorLine = new HBox(6);
        Label nameLbl = new Label(authorName);
        nameLbl.setTextFill(Color.web("#e7e9ea"));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 13));

        Label handleLbl = new Label("@" + authorHandle);
        handleLbl.setTextFill(Color.web("#71767b"));
        handleLbl.setFont(Font.font("System", 13));

        authorLine.getChildren().addAll(nameLbl, handleLbl);

        String previewText = parentContent;
        if (previewText.length() > 110) {
            previewText = previewText.substring(0, 107) + "...";
        }
        Label textLbl = new Label(previewText);
        textLbl.setTextFill(Color.web("#71767b"));
        textLbl.setFont(Font.font("System", 13));
        textLbl.setWrapText(true);

        refBox.getChildren().addAll(authorLine, textLbl);

        if (parentMedia != null && !parentMedia.isBlank()) {
            String indicatorText = TweetMediaHelper.isVideoPath(parentMedia) ? "🎥 Video attached" : "📷 Photo attached";
            Label mediaLbl = new Label(indicatorText);
            mediaLbl.setTextFill(Color.web("#1d9bf0"));
            mediaLbl.setFont(Font.font("System", 12));
            refBox.getChildren().add(mediaLbl);
        }

        refBox.setOnMouseEntered(e -> refBox.setStyle(
                "-fx-background-color: #1c1f23; " +
                "-fx-border-color: #1d9bf0; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 8 12 8 12; " +
                "-fx-cursor: hand;"
        ));
        refBox.setOnMouseExited(e -> refBox.setStyle(
                "-fx-background-color: #16181c; " +
                "-fx-border-color: #333333; " +
                "-fx-border-radius: 10; " +
                "-fx-background-radius: 10; " +
                "-fx-padding: 8 12 8 12; " +
                "-fx-cursor: hand;"
        ));

        refBox.setOnMouseClicked(e -> {
            e.consume();
            NavigationManager.switchScene("/views/Feed.fxml");
        });

        return refBox;
    }

    private StoredTweet engagementTarget(StoredTweet tweet) {
        if (tweet.isRetweet() && tweet.getRetweetOfId() != null) {
            StoredTweet original = TweetStore.getInstance().findById(tweet.getRetweetOfId());
            if (original != null) {
                return original;
            }
        }
        return tweet;
    }

    private void applyReplyStyle(Button button, StoredTweet tweet) {
        button.setText("💬 " + tweet.getReplies());
        button.setStyle(tweet.getReplies() > 0 ? STYLE_REPLY_ACTIVE : STYLE_ACTION_IDLE);
    }

    private void applyRetweetStyle(Button button, StoredTweet tweet) {
        button.setText("🔁 " + tweet.getRetweets());
        button.setStyle(tweet.isRetweetedByCurrentUser() ? STYLE_RETWEET_ACTIVE : STYLE_ACTION_IDLE);
    }

    private void applyLikeStyle(Button button, StoredTweet tweet) {
        if (tweet.isLikedByCurrentUser()) {
            button.setText("❤ " + tweet.getLikes());
            button.setStyle(STYLE_LIKE_ACTIVE);
        } else {
            button.setText("♡ " + tweet.getLikes());
            button.setStyle(STYLE_ACTION_IDLE);
        }
    }

    private void applyBookmarkStyle(Button button, StoredTweet tweet) {
        button.setText("🔖");
        button.setStyle(tweet.isBookmarked() ? STYLE_BOOKMARK_ACTIVE : STYLE_ACTION_IDLE);
    }

    private void renderTweetMediaIfPresent(VBox contentStack, StoredTweet tweet) {
        String mediaPath = tweet.getMediaPath();
        if (mediaPath != null && !mediaPath.isBlank()) {
            Node mediaNode = TweetMediaHelper.createMediaNode(mediaPath, 380, 220);
            if (mediaNode != null) {
                contentStack.getChildren().add(mediaNode);
            }
        }
    }

    private void rebuildReplyPanel(VBox replyPanel, StoredTweet parent, Button replyButton) {
        replyPanel.getChildren().clear();

        List<StoredTweet> replies = TweetStore.getInstance().getReplies(parent.getId());

        if (replies.isEmpty()) {
            Label empty = new Label("No replies yet — be the first to reply.");
            empty.setTextFill(Color.web("#71767b"));
            empty.setFont(Font.font("System", 13));
            replyPanel.getChildren().add(empty);
        }
        else {
            VBox thread = new VBox(0);
            for (StoredTweet reply : replies) {
                thread.getChildren().add(buildNestedReplyNode(reply));
            }
            replyPanel.getChildren().add(thread);
        }

        TextArea replyInput = new TextArea();
        replyInput.setPromptText("Post your reply");
        replyInput.setPrefRowCount(2);
        replyInput.setWrapText(true);
        replyInput.setStyle(STYLE_COMPOSE_AREA);
        replyInput.setMaxWidth(Double.MAX_VALUE);

        // Reply Media Attachment Preview Container
        StackPane replyMediaPreviewContainer = new StackPane();
        replyMediaPreviewContainer.setAlignment(Pos.TOP_RIGHT);
        replyMediaPreviewContainer.setMaxHeight(160);
        replyMediaPreviewContainer.setMaxWidth(340);
        replyMediaPreviewContainer.setVisible(false);
        replyMediaPreviewContainer.setManaged(false);
        replyMediaPreviewContainer.setStyle("-fx-background-color: #16181c; -fx-background-radius: 10; -fx-border-color: #333333; -fx-border-radius: 10;");

        VBox replyMediaPreviewBox = new VBox();
        replyMediaPreviewBox.setAlignment(Pos.CENTER);

        final String[] replySelectedMediaPath = new String[1];

        Button removeReplyMediaBtn = new Button("✕");
        removeReplyMediaBtn.setStyle("-fx-background-color: rgba(15, 20, 25, 0.75); -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 24px; -fx-min-height: 24px; -fx-font-size: 11px; -fx-cursor: hand;");
        StackPane.setMargin(removeReplyMediaBtn, new Insets(6, 6, 0, 0));
        removeReplyMediaBtn.setOnAction(e -> {
            replySelectedMediaPath[0] = null;
            replyMediaPreviewBox.getChildren().clear();
            replyMediaPreviewContainer.setVisible(false);
            replyMediaPreviewContainer.setManaged(false);
        });

        replyMediaPreviewContainer.getChildren().addAll(replyMediaPreviewBox, removeReplyMediaBtn);

        HBox replyActions = new HBox(12);
        replyActions.setAlignment(Pos.CENTER_RIGHT);

        Button attachMediaBtn = new Button("🖼️");
        attachMediaBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-font-size: 16px; -fx-padding: 4; -fx-cursor: hand;");
        attachMediaBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Attach Media to Reply");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Media Files (*.png, *.jpg, *.jpeg, *.gif, *.mp4, *.m4v)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.mp4", "*.m4v"),
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                    new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.m4v")
            );
            Stage stage = (Stage) replyInput.getScene().getWindow();
            File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                replySelectedMediaPath[0] = file.toURI().toString();
                Node previewNode = TweetMediaHelper.createMediaNode(replySelectedMediaPath[0], 340, 150);
                if (previewNode != null) {
                    replyMediaPreviewBox.getChildren().clear();
                    replyMediaPreviewBox.getChildren().add(previewNode);
                    replyMediaPreviewContainer.setVisible(true);
                    replyMediaPreviewContainer.setManaged(true);
                } else {
                    replySelectedMediaPath[0] = null;
                }
            }
        });

        Button emojiBtn = new Button("😊");
        emojiBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-font-size: 16px; -fx-padding: 4; -fx-cursor: hand;");
        emojiBtn.setOnAction(e -> client.EmojiPickerHelper.showEmojiPicker(emojiBtn, replyInput));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button sendReply = new Button("Reply");
        sendReply.setStyle(STYLE_POST_BTN);
        sendReply.setOnAction(e -> {
            String text = replyInput.getText() != null ? replyInput.getText().trim() : "";
            if (text.isEmpty() && replySelectedMediaPath[0] == null) {
                return;
            }
            TweetStore.getInstance().addReply(
                    parent.getId(),
                    text,
                    currentUsername(),
                    currentDisplayName(),
                    replySelectedMediaPath[0]
            );
            applyReplyStyle(replyButton, parent);
            rebuildReplyPanel(replyPanel, parent, replyButton);
        });

        replyActions.getChildren().addAll(attachMediaBtn, emojiBtn, spacer, sendReply);

        replyPanel.getChildren().addAll(replyInput, replyMediaPreviewContainer, replyActions);
    }

    private VBox buildNestedReplyNode(StoredTweet reply) {
        VBox node = new VBox(4);
        node.setPadding(new Insets(10, 0, 10, 12));
        node.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 0 2; -fx-padding: 8 0 8 12;");

        HBox header = new HBox(8);
        Label name = new Label(safeName(reply.getAuthorDisplayName()));
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("System", FontWeight.BOLD, 14));

        Label handle = new Label("@" + safeUsername(reply.getAuthorUsername()));
        handle.setTextFill(Color.web("#71767b"));
        handle.setFont(Font.font("System", 13));

        Label time = createTimestampLabel(reply.getCreatedAt());
        time.setFont(Font.font("System", 13));

        header.getChildren().addAll(name, handle, time);

        if (isOwnedByCurrentUser(reply)) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(spacer, createDeleteButton(reply.getId(), () -> refreshProfileData()));
        }

        Label body = new Label(reply.getContent());
        body.setTextFill(Color.web("#e7e9ea"));
        body.setFont(Font.font("System", 14));
        body.setWrapText(true);
        body.setMaxWidth(400);

        node.getChildren().addAll(header, body);

        if (reply.getMediaPath() != null && !reply.getMediaPath().isBlank()) {
            Node mediaNode = TweetMediaHelper.createMediaNode(reply.getMediaPath(), 340, 180);
            if (mediaNode != null) {
                node.getChildren().add(mediaNode);
            }
        }

        // Action Toolbar for Nested Reply (4 Buttons: Reply, Retweet, Like, Bookmark)
        HBox actionToolbar = new HBox(30);
        actionToolbar.setStyle("-fx-padding: 4 0 0 0;");

        VBox replyPanel = new VBox(8);
        replyPanel.setVisible(false);
        replyPanel.setManaged(false);
        replyPanel.setStyle("-fx-padding: 6 0 0 0;");

        Button replyBtn = new Button();
        applyReplyStyle(replyBtn, reply);
        replyBtn.setOnAction(event -> {
            boolean open = !replyPanel.isVisible();
            replyPanel.setVisible(open);
            replyPanel.setManaged(open);
            if (open) {
                rebuildReplyPanel(replyPanel, reply, replyBtn);
            }
        });

        Button retweetBtn = new Button();
        applyRetweetStyle(retweetBtn, reply);
        retweetBtn.setOnAction(event -> {
            TweetStore.getInstance().toggleRetweet(
                    reply.getId(),
                    currentUsername(),
                    currentDisplayName()
            );
            refreshProfileData();
        });

        Button likeBtn = new Button();
        applyLikeStyle(likeBtn, reply);
        likeBtn.setOnAction(event -> {
            TweetStore.getInstance().toggleLike(reply.getId());
            applyLikeStyle(likeBtn, reply);
        });

        Button bookmarkBtn = new Button();
        applyBookmarkStyle(bookmarkBtn, reply);
        bookmarkBtn.setOnAction(event -> {
            TweetStore.getInstance().toggleBookmark(reply.getId());
            applyBookmarkStyle(bookmarkBtn, reply);
        });

        actionToolbar.getChildren().addAll(replyBtn, retweetBtn, likeBtn, bookmarkBtn);
        node.getChildren().addAll(actionToolbar, replyPanel);

        return node;
    }

    private Button createDeleteButton(int tweetId, Runnable afterDelete) {
        Button deleteButton = new Button("🗑");
        deleteButton.setStyle(STYLE_DELETE_BTN);
        deleteButton.setOnAction(event -> {
            String username = currentUsername();
            if (TweetStore.getInstance().deleteTweet(tweetId, username)) {
                afterDelete.run();
            }
        });
        return deleteButton;
    }

    private boolean isOwnedByCurrentUser(StoredTweet tweet) {
        return currentUsername().equals(tweet.getAuthorUsername());
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "Guest" : name;
    }

    private String safeUsername(String username) {
        return (username == null || username.isBlank()) ? "developer" : username;
    }

    private String currentUsername() {
        String username = UserSession.getInstance().getUsername();
        return username != null ? username : "developer";
    }

    private String currentDisplayName() {
        String displayName = UserSession.getInstance().getDisplayName();
        return displayName != null ? displayName : "Guest";
    }

    @FXML
    private void handleOpenDrawer() {
        SideDrawerHelper.open(drawerOverlay, drawerPanel);
    }

    @FXML
    private void handleCloseDrawer() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML
    private void handleGoToHome() {
        NavigationManager.switchScene("/views/Feed.fxml");
    }

    @FXML
    private void handleGoToSearch() {
        NavigationManager.switchScene("/views/Search.fxml");
    }

    @FXML
    private void handleCreatePost() {
        NavigationManager.switchScene("/views/Compose.fxml");
    }

    @FXML
    private void handleGoToProfile() {
        // Already on Profile; close drawer if open
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML
    private void handleGoToBookmarks() {
        NavigationManager.switchScene("/views/Bookmarks.fxml");
    }

    @FXML
    private void handleLogout() {
        LogoutHelper.showConfirmation();
    }

    public void refreshProfileData() {
        shared.models.User currentUser = UserSession.getInstance().getCurrentUser();

        if (currentUser != null) {
            String activeDisplayName = currentUser.getDisplayName();
            String activeUsername = currentUser.getUsername();
            String activeBio = currentUser.getBio();

            nameLabel.setText(activeDisplayName != null ? activeDisplayName : "Active User");
            usernameLabel.setText(activeUsername != null ? "@" + activeUsername : "@user");

            if (bioLabel != null) {
                bioLabel.setText(activeBio != null ? activeBio : "No bio available");
            }
        }
        else {
            nameLabel.setText("Active User");
            usernameLabel.setText("@user");
        }

        userTweetsContainer.getChildren().clear();
        liveTimestamps.clear();
        loadUserOwnTweets();
        loadProfileImages();
    }

    private void loadProfileImages() {
        String avatarPath = UserSession.getInstance().getAvatarImagePath();
        if (avatarPath != null && !avatarPath.isBlank()) {
            try {
                String uriString;
                if (avatarPath.startsWith("file:") || avatarPath.startsWith("http:") || avatarPath.startsWith("https:")) {
                    uriString = avatarPath;
                } else {
                    File file = new File(avatarPath);
                    uriString = file.exists() ? file.toURI().toString() : null;
                }

                if (uriString != null) {
                    Image img = new Image(uriString, 180, 180, false, true);
                    if (!img.isError()) {
                        if (avatarImageView != null) {
                            avatarImageView.setImage(img);
                            avatarImageView.setFitWidth(90);
                            avatarImageView.setFitHeight(90);
                            avatarImageView.setPreserveRatio(false);
                            Circle clip = new Circle(45, 45, 45);
                            avatarImageView.setClip(clip);
                            avatarImageView.setVisible(true);
                            avatarImageView.setManaged(true);
                        }
                        if (avatarPlaceholder != null) avatarPlaceholder.setVisible(false);
                        if (avatarPlaceholderIcon != null) avatarPlaceholderIcon.setVisible(false);
                    } else {
                        resetAvatarUI();
                    }
                } else {
                    resetAvatarUI();
                }
            } catch (Exception e) {
                resetAvatarUI();
            }
        } else {
            resetAvatarUI();
        }

        String bannerPath = UserSession.getInstance().getBannerImagePath();
        if (bannerPath != null && !bannerPath.isBlank()) {
            try {
                String uriString;
                if (bannerPath.startsWith("file:") || bannerPath.startsWith("http:") || bannerPath.startsWith("https:")) {
                    uriString = bannerPath;
                } else {
                    File file = new File(bannerPath);
                    uriString = file.exists() ? file.toURI().toString() : null;
                }

                if (uriString != null) {
                    Image img = new Image(uriString, 1200, 300, false, true);
                    if (!img.isError()) {
                        if (bannerImageView != null) {
                            bannerImageView.setImage(img);
                            bannerImageView.setFitHeight(150);
                            bannerImageView.setPreserveRatio(false);
                            bannerImageView.setVisible(true);
                            bannerImageView.setManaged(true);
                        }
                        if (bannerPlaceholder != null) bannerPlaceholder.setVisible(false);
                    } else {
                        resetBannerUI();
                    }
                } else {
                    resetBannerUI();
                }
            } catch (Exception e) {
                resetBannerUI();
            }
        } else {
            resetBannerUI();
        }
    }

    private void resetAvatarUI() {
        if (avatarImageView != null) {
            avatarImageView.setImage(null);
            avatarImageView.setVisible(false);
            avatarImageView.setManaged(false);
        }
        if (avatarPlaceholder != null) avatarPlaceholder.setVisible(true);
        if (avatarPlaceholderIcon != null) avatarPlaceholderIcon.setVisible(true);
    }

    private void resetBannerUI() {
        if (bannerImageView != null) {
            bannerImageView.setImage(null);
            bannerImageView.setVisible(false);
            bannerImageView.setManaged(false);
        }
        if (bannerPlaceholder != null) bannerPlaceholder.setVisible(true);
    }

    @FXML
    private void handleAvatarClick() {
        String currentPath = UserSession.getInstance().getAvatarImagePath();
        if (currentPath != null && !currentPath.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Profile Photo");
            alert.setHeaderText("Profile Photo Options");
            alert.setContentText("Choose an option for your profile photo:");

            ButtonType chooseBtn = new ButtonType("Choose new photo", ButtonBar.ButtonData.OK_DONE);
            ButtonType removeBtn = new ButtonType("Remove photo", ButtonBar.ButtonData.OTHER);
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(chooseBtn, removeBtn, cancelBtn);

            try {
                DialogPane dialogPane = alert.getDialogPane();
                if (getClass().getResource("/styles/twitter.css") != null) {
                    dialogPane.getStylesheets().add(getClass().getResource("/styles/twitter.css").toExternalForm());
                }
                dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #333333; -fx-border-width: 1px;");
            } catch (Exception ignored) {}

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == chooseBtn) {
                    chooseNewAvatar();
                } else if (result.get() == removeBtn) {
                    UserSession.getInstance().setAvatarImagePath(null);
                    loadProfileImages();
                }
            }
        } else {
            chooseNewAvatar();
        }
    }

    @FXML
    private void handleBannerClick() {
        String currentPath = UserSession.getInstance().getBannerImagePath();
        if (currentPath != null && !currentPath.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Header Banner");
            alert.setHeaderText("Header Banner Options");
            alert.setContentText("Choose an option for your header banner:");

            ButtonType chooseBtn = new ButtonType("Choose new photo", ButtonBar.ButtonData.OK_DONE);
            ButtonType removeBtn = new ButtonType("Remove photo", ButtonBar.ButtonData.OTHER);
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(chooseBtn, removeBtn, cancelBtn);

            try {
                DialogPane dialogPane = alert.getDialogPane();
                if (getClass().getResource("/styles/twitter.css") != null) {
                    dialogPane.getStylesheets().add(getClass().getResource("/styles/twitter.css").toExternalForm());
                }
                dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #333333; -fx-border-width: 1px;");
            } catch (Exception ignored) {}

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == chooseBtn) {
                    chooseNewBanner();
                } else if (result.get() == removeBtn) {
                    UserSession.getInstance().setBannerImagePath(null);
                    loadProfileImages();
                }
            }
        } else {
            chooseNewBanner();
        }
    }

    private void chooseNewAvatar() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Avatar");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("WebP Files", "*.webp")
        );
        Stage stage = (Stage) nameLabel.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            UserSession.getInstance().setAvatarImagePath(file.toURI().toString());
            loadProfileImages();
        }
    }

    private void chooseNewBanner() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Header Banner");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.webp)", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("WebP Files", "*.webp")
        );
        Stage stage = (Stage) nameLabel.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            UserSession.getInstance().setBannerImagePath(file.toURI().toString());
            loadProfileImages();
        }
    }
}
