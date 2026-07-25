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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BookmarksController {

    @FXML
    private VBox bookmarksContainer;

    @FXML
    private HBox drawerOverlay;

    @FXML
    private VBox drawerPanel;

    @FXML
    private Label drawerDisplayName;

    @FXML
    private Label drawerUsername;

    private final List<TimestampLabel> liveTimestamps = new ArrayList<>();
    private Timeline timeRefreshTimeline;

    private static final String STYLE_ACTION_IDLE =
            "-fx-background-color: transparent; -fx-text-fill: #71767b; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_RETWEET_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #00ba7c; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_LIKE_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #f91880; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_BOOKMARK_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-padding: 0; -fx-cursor: hand;";

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        loadBookmarks();
        startTimestampRefresh();
    }

    private void loadBookmarks() {
        bookmarksContainer.getChildren().clear();
        liveTimestamps.clear();

        TweetStore.getInstance().seedIfEmpty();
        List<StoredTweet> bookmarkedList = TweetStore.getInstance().getBookmarkedTweets();

        if (bookmarkedList.isEmpty()) {
            renderEmptyState();
            return;
        }

        for (StoredTweet tweet : bookmarkedList) {
            renderBookmarkTweetCard(tweet);
        }
    }

    private void renderEmptyState() {
        VBox emptyBox = new VBox(8);
        emptyBox.setStyle("-fx-padding: 80 40 40 40; -fx-alignment: center;");

        Label titleLabel = new Label("No Bookmarks yet");
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label subLabel = new Label("Bookmark posts to easily find them again in the future.");
        subLabel.setTextFill(Color.web("#71767b"));
        subLabel.setFont(Font.font("System", 14));
        subLabel.setWrapText(true);

        emptyBox.getChildren().addAll(titleLabel, subLabel);
        bookmarksContainer.getChildren().add(emptyBox);
    }

    private void renderBookmarkTweetCard(StoredTweet tweet) {
        VBox card = new VBox(6);
        card.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0; -fx-padding: 12 16 12 16;");

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

        String headerName = tweet.isRetweet()
                ? safeName(tweet.getOriginalAuthorDisplayName())
                : safeName(tweet.getAuthorDisplayName());
        String headerHandle = tweet.isRetweet()
                ? safeUsername(tweet.getOriginalAuthorUsername())
                : safeUsername(tweet.getAuthorUsername());

        HBox headerRow = new HBox(8);
        Label displayName = new Label(headerName);
        displayName.setTextFill(Color.WHITE);
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label userHandle = new Label("@" + headerHandle);
        userHandle.setTextFill(Color.web("#71767b"));
        userHandle.setFont(Font.font("System", 14));

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        Label bodyText = new Label(tweet.getContent());
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setFont(Font.font("System", 15));
        bodyText.setWrapText(true);

        HBox actionToolbar = new HBox(40);
        actionToolbar.setStyle("-fx-padding: 6 0 0 0;");

        Button retweetButton = new Button();
        applyRetweetStyle(retweetButton, tweet);
        retweetButton.setOnAction(event -> {
            TweetStore.getInstance().toggleRetweet(
                    tweet.getId(),
                    currentUsername(),
                    currentDisplayName()
            );
            loadBookmarks();
        });

        Button likeButton = new Button();
        applyLikeStyle(likeButton, tweet);
        likeButton.setOnAction(event -> {
            TweetStore.getInstance().toggleLike(tweet.getId());
            applyLikeStyle(likeButton, tweet);
        });

        Button bookmarkButton = new Button();
        applyBookmarkStyle(bookmarkButton, tweet);
        bookmarkButton.setOnAction(event -> {
            TweetStore.getInstance().toggleBookmark(tweet.getId());
            loadBookmarks();
        });

        actionToolbar.getChildren().addAll(retweetButton, likeButton, bookmarkButton);

        contentStack.getChildren().addAll(headerRow, bodyText, actionToolbar);
        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);

        bookmarksContainer.getChildren().add(card);
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
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML
    private void handleGoToBookmarks() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML
    private void handleLogout() {
        LogoutHelper.showConfirmation();
    }
}
