package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetStore;
import client.TweetStore.StoredTweet;
import client.TweetTimeFormatter;
import client.UserSession;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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

public class FeedController {

    @FXML
    private VBox timelineContainer;

    @FXML
    private HBox drawerOverlay;

    @FXML
    private VBox drawerPanel;

    @FXML
    private Label drawerDisplayName;

    @FXML
    private Label drawerUsername;

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
            "-fx-control-inner-background: #000000; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #71767b; "
                    + "-fx-border-color: #333333; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String STYLE_POST_BTN =
            "-fx-background-color: #1d9bf0; -fx-text-fill: #ffffff; -fx-background-radius: 20; -fx-font-weight: bold;";
    private static final String STYLE_DELETE_BTN =
            "-fx-background-color: transparent; -fx-text-fill: #f4212e; -fx-padding: 0; -fx-cursor: hand; -fx-font-size: 14;";

    /** Labels that need Twitter-style relative times refreshed while the feed is open. */
    private final List<TimestampLabel> liveTimestamps = new ArrayList<>();
    private Timeline timeRefreshTimeline;

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadTimeline();
        startTimestampRefresh();
    }

    private void loadTimeline() {
        timelineContainer.getChildren().clear();
        liveTimestamps.clear();

        TweetStore store = TweetStore.getInstance();
        store.seedIfEmpty();

        for (StoredTweet tweet : store.getTimelineTweets()) {
            renderTweetCard(tweet, false);
        }
    }

    private void startTimestampRefresh() {
        if (timeRefreshTimeline != null) {
            timeRefreshTimeline.stop();
        }
        // Refresh relative labels so "now" → "1s" → "1m" while the user stays on Home
        timeRefreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(15), e -> refreshLiveTimestamps())
        );
        timeRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        timeRefreshTimeline.play();
    }

    private void refreshLiveTimestamps() {
        for (TimestampLabel entry : liveTimestamps) {
            entry.label.setText(TweetTimeFormatter.formatFeedDot(entry.createdAt));
        }
    }

    private Label createTimestampLabel(Instant createdAt) {
        Label timestamp = new Label(TweetTimeFormatter.formatFeedDot(createdAt));
        timestamp.setTextFill(Color.web("#71767b"));
        timestamp.setFont(Font.font("System", 14));
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

    /**
     * @param isNestedReply when true, card is indented under a parent (no nested reply UI)
     */
    private void renderTweetCard(StoredTweet tweet, boolean isNestedReply) {
        VBox card = new VBox(6);
        card.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0; -fx-padding: 12 16 12 16;");
        if (isNestedReply) {
            card.setPadding(new Insets(8, 8, 8, 36));
            card.setStyle("-fx-border-color: #222222; -fx-border-width: 0 0 1 0; -fx-padding: 10 12 10 36;");
        }

        // Retweet context line
        if (tweet.isRetweet()) {
            Label repostLabel = new Label("🔁 " + safeName(tweet.getAuthorDisplayName()) + " reposted");
            repostLabel.setTextFill(Color.web("#71767b"));
            repostLabel.setFont(Font.font("System", 13));
            card.getChildren().add(repostLabel);
        }
        else if (tweet.isReply()) {
            String replyTo = tweet.getOriginalAuthorUsername() != null
                    ? "@" + tweet.getOriginalAuthorUsername()
                    : "someone";
            Label replyLabel = new Label("💬 Replying to " + replyTo);
            replyLabel.setTextFill(Color.web("#1d9bf0"));
            replyLabel.setFont(Font.font("System", 13));
            card.getChildren().add(replyLabel);
        }

        HBox tweetRow = new HBox(12);

        VBox avatarBox = new VBox();
        Label avatar = new Label("👤");
        avatar.setFont(Font.font("System", 24));
        avatar.setTextFill(Color.web("#71767b"));
        avatarBox.getChildren().add(avatar);

        VBox contentStack = new VBox(4);
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        // For retweet cards, show original author in the header (X-style)
        String headerName;
        String headerHandle;
        if (tweet.isRetweet()) {
            headerName = tweet.getOriginalAuthorDisplayName() != null
                    ? tweet.getOriginalAuthorDisplayName()
                    : "User";
            headerHandle = tweet.getOriginalAuthorUsername() != null
                    ? tweet.getOriginalAuthorUsername()
                    : "user";
        }
        else {
            headerName = safeName(tweet.getAuthorDisplayName());
            headerHandle = safeUsername(tweet.getAuthorUsername());
        }

        HBox headerRow = new HBox(8);
        Label displayName = new Label(headerName);
        displayName.setTextFill(Color.WHITE);
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label userHandle = new Label("@" + headerHandle);
        userHandle.setTextFill(Color.web("#71767b"));
        userHandle.setFont(Font.font("System", 14));

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        // Delete only on posts you authored (original, retweet card, or reply)
        if (isOwnedByCurrentUser(tweet)) {
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button deleteButton = createDeleteButton(tweet.getId(), () -> loadTimeline());
            headerRow.getChildren().addAll(spacer, deleteButton);
        }

        Label bodyText = new Label(tweet.getContent());
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setFont(Font.font("System", 15));
        bodyText.setWrapText(true);
        bodyText.setMaxWidth(isNestedReply ? 360 : 420);

        contentStack.getChildren().addAll(headerRow, bodyText);
        renderTweetMediaIfPresent(contentStack, tweet);

        // Engagement toolbar uses the original for counts on retweet cards
        StoredTweet engagementTarget = engagementTarget(tweet);

        if (!isNestedReply) {
            HBox actionToolbar = new HBox(40);
            actionToolbar.setStyle("-fx-padding: 6 0 0 0;");

            // Reply panel (composer + thread) toggled by mention button
            VBox replyPanel = new VBox(8);
            replyPanel.setVisible(false);
            replyPanel.setManaged(false);
            replyPanel.setStyle("-fx-padding: 8 0 0 0;");

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

            Button retweetButton = new Button();
            applyRetweetStyle(retweetButton, engagementTarget);
            retweetButton.setOnAction(event -> {
                TweetStore.getInstance().toggleRetweet(
                        engagementTarget.getId(),
                        currentUsername(),
                        currentDisplayName()
                );
                // Full reload so the new retweet card (or its removal) shows on the timeline
                loadTimeline();
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

            actionToolbar.getChildren().addAll(replyButton, retweetButton, likeButton, bookmarkButton);
            contentStack.getChildren().add(actionToolbar);
            contentStack.getChildren().add(replyPanel);
        }

        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);
        timelineContainer.getChildren().add(card);
    }

    /**
     * Builds the inline reply thread: list of replies + compose box.
     */
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
                javafx.scene.Node previewNode = client.TweetMediaHelper.createMediaNode(replySelectedMediaPath[0], 340, 150);
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

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
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
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            // Reload whole feed so parent reply counts stay correct
            header.getChildren().addAll(spacer, createDeleteButton(reply.getId(), () -> loadTimeline()));
        }

        Label body = new Label(reply.getContent());
        body.setTextFill(Color.web("#e7e9ea"));
        body.setFont(Font.font("System", 14));
        body.setWrapText(true);
        body.setMaxWidth(400);

        node.getChildren().addAll(header, body);

        if (reply.getMediaPath() != null && !reply.getMediaPath().isBlank()) {
            javafx.scene.Node mediaNode = client.TweetMediaHelper.createMediaNode(reply.getMediaPath(), 340, 180);
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
            loadTimeline();
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
            boolean deleted = TweetStore.getInstance().deleteTweet(tweetId, currentUsername());
            if (deleted && afterDelete != null) {
                afterDelete.run();
            }
        });
        return deleteButton;
    }

    private boolean isOwnedByCurrentUser(StoredTweet tweet) {
        return currentUsername().equals(tweet.getAuthorUsername());
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
        // Highlight when there are replies (thread has content)
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
        }
        else {
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
            javafx.scene.Node mediaNode = client.TweetMediaHelper.createMediaNode(mediaPath, 380, 220);
            if (mediaNode != null) {
                contentStack.getChildren().add(mediaNode);
            }
        }
    }

    private String currentUsername() {
        String username = UserSession.getInstance().getUsername();
        return username != null ? username : "developer";
    }

    private String currentDisplayName() {
        String displayName = UserSession.getInstance().getDisplayName();
        return displayName != null ? displayName : "Guest";
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "Guest" : name;
    }

    private String safeUsername(String username) {
        return (username == null || username.isBlank()) ? "developer" : username;
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
        // Already on Home; close drawer if it was open
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
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
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML
    private void handleGoToBookmarks() {
        NavigationManager.switchScene("/views/Bookmarks.fxml");
    }

    @FXML
    private void handleLogout() {
        LogoutHelper.showConfirmation();
    }
}
