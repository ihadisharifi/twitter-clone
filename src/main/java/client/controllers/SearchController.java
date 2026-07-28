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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SearchController {

    @FXML
    private TextField searchInputField;

    @FXML
    private VBox searchResultsContainer;

    @FXML
    private HBox drawerOverlay;

    @FXML
    private VBox drawerPanel;

    @FXML
    private Label drawerDisplayName;

    @FXML
    private Label drawerUsername;

    @FXML
    private Button themeToggleButton;

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
        client.ThemeManager.updateThemeButton(themeToggleButton);
        TweetStore.getInstance().seedIfEmpty();

        if (searchInputField != null) {
            searchInputField.textProperty().addListener((obs, oldVal, newVal) -> performSearch(newVal));
        }

        renderEmptyState("Search X", "Search for posts, topics, or accounts");
        startTimestampRefresh();
    }

    @FXML
    private void handleToggleTheme() {
        if (drawerOverlay != null && drawerOverlay.getScene() != null) {
            client.ThemeManager.toggleTheme(drawerOverlay.getScene());
            client.ThemeManager.updateThemeButton(themeToggleButton);
        }
    }

    private void startTimestampRefresh() {
        if (timeRefreshTimeline != null) {
            timeRefreshTimeline.stop();
        }
        timeRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshLiveTimestamps()));
        timeRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        timeRefreshTimeline.play();
    }

    private void refreshLiveTimestamps() {
        for (TimestampLabel entry : liveTimestamps) {
            entry.label.setText("· " + TweetTimeFormatter.formatRelative(entry.createdAt));
        }
    }

    private Label createTimestampLabel(Instant createdAt) {
        Label timestamp = new Label("· " + TweetTimeFormatter.formatRelative(createdAt));
        timestamp.getStyleClass().add("tweet-timestamp");
        timestamp.setFont(Font.font("System", 14));
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

    @FXML
    private void handleSearchAction() {
        if (searchInputField != null) {
            performSearch(searchInputField.getText());
        }
    }

    private void performSearch(String query) {
        liveTimestamps.clear();

        if (query == null || query.isBlank()) {
            renderEmptyState("Search X", "Search for posts, topics, or accounts");
            return;
        }

        List<StoredTweet> results = TweetStore.getInstance().searchTweets(query.trim());
        if (results.isEmpty()) {
            renderEmptyState("No results for \"" + query.trim() + "\"", "Try searching for something else");
            return;
        }

        searchResultsContainer.getChildren().clear();
        for (StoredTweet tweet : results) {
            renderSearchTweetCard(tweet);
        }
    }

    private void renderEmptyState(String title, String subtitle) {
        searchResultsContainer.getChildren().clear();
        VBox emptyBox = new VBox(10);
        emptyBox.setStyle("-fx-padding: 60 40 40 40; -fx-alignment: center;");

        Label iconLabel = new Label("🔍");
        iconLabel.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("primary-text");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("secondary-text");
        subLabel.setFont(Font.font("System", 14));
        subLabel.setWrapText(true);

        emptyBox.getChildren().addAll(iconLabel, titleLabel, subLabel);
        searchResultsContainer.getChildren().add(emptyBox);
    }

    private void renderSearchTweetCard(StoredTweet tweet) {
        VBox card = new VBox(6);
        card.getStyleClass().add("tweet-card");

        HBox tweetRow = new HBox(12);

        VBox avatarBox = new VBox();
        Label avatar = new Label("👤");
        avatar.setFont(Font.font("System", 24));
        avatar.getStyleClass().add("secondary-text");
        avatarBox.getChildren().add(avatar);

        VBox contentStack = new VBox(4);
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        HBox headerRow = new HBox(8);
        Label displayName = new Label(tweet.getAuthorDisplayName() != null ? tweet.getAuthorDisplayName() : "User");
        displayName.getStyleClass().add("tweet-author");
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label userHandle = new Label("@" + (tweet.getAuthorUsername() != null ? tweet.getAuthorUsername() : "user"));
        userHandle.getStyleClass().add("tweet-username");
        userHandle.setFont(Font.font("System", 14));

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        Label bodyText = new Label(tweet.getContent());
        bodyText.getStyleClass().add("tweet-text");
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
            performSearch(searchInputField != null ? searchInputField.getText() : "");
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
            applyBookmarkStyle(bookmarkButton, tweet);
        });

        actionToolbar.getChildren().addAll(retweetButton, likeButton, bookmarkButton);

        contentStack.getChildren().addAll(headerRow, bodyText);
        renderTweetMediaIfPresent(contentStack, tweet);
        contentStack.getChildren().add(actionToolbar);
        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);

        searchResultsContainer.getChildren().add(card);
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
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
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
