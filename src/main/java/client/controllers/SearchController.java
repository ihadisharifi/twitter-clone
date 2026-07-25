package client.controllers;

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

    private final List<TimestampLabel> liveTimestamps = new ArrayList<>();
    private Timeline timeRefreshTimeline;

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        TweetStore.getInstance().seedIfEmpty();

        if (searchInputField != null) {
            searchInputField.textProperty().addListener((obs, oldVal, newVal) -> performSearch(newVal));
        }

        renderEmptyState("Search X", "Search for posts, topics, or accounts");
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

    @FXML
    private void handleSearchAction() {
        if (searchInputField != null) {
            performSearch(searchInputField.getText());
        }
    }

    private void performSearch(String query) {
        searchResultsContainer.getChildren().clear();
        liveTimestamps.clear();

        if (query == null || query.trim().isEmpty()) {
            renderEmptyState("Search X", "Search for posts, topics, or accounts");
            return;
        }

        List<StoredTweet> matches = TweetStore.getInstance().searchTweets(query);

        if (matches.isEmpty()) {
            renderEmptyState("No results for \"" + query.trim() + "\"", "Try searching for another term or username");
            return;
        }

        for (StoredTweet tweet : matches) {
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
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label subLabel = new Label(subtitle);
        subLabel.setTextFill(Color.web("#71767b"));
        subLabel.setFont(Font.font("System", 14));
        subLabel.setWrapText(true);

        emptyBox.getChildren().addAll(iconLabel, titleLabel, subLabel);
        searchResultsContainer.getChildren().add(emptyBox);
    }

    private void renderSearchTweetCard(StoredTweet tweet) {
        VBox card = new VBox(6);
        card.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0; -fx-padding: 12 16 12 16;");

        HBox tweetRow = new HBox(12);

        VBox avatarBox = new VBox();
        Label avatar = new Label("👤");
        avatar.setFont(Font.font("System", 24));
        avatar.setTextFill(Color.web("#71767b"));
        avatarBox.getChildren().add(avatar);

        VBox contentStack = new VBox(4);
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        HBox headerRow = new HBox(8);
        Label displayName = new Label(tweet.getAuthorDisplayName() != null ? tweet.getAuthorDisplayName() : "User");
        displayName.setTextFill(Color.WHITE);
        displayName.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label userHandle = new Label("@" + (tweet.getAuthorUsername() != null ? tweet.getAuthorUsername() : "user"));
        userHandle.setTextFill(Color.web("#71767b"));
        userHandle.setFont(Font.font("System", 14));

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        Label bodyText = new Label(tweet.getContent());
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setFont(Font.font("System", 15));
        bodyText.setWrapText(true);

        contentStack.getChildren().addAll(headerRow, bodyText);
        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);

        searchResultsContainer.getChildren().add(card);
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
        UserSession.getInstance().clearSession();
        TweetStore.getInstance().clear();
        NavigationManager.switchScene("/views/Login.fxml");
    }
}
