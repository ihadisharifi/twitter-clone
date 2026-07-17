package client.controllers;

import client.NavigationManager;
import client.TweetStore;
import client.TweetStore.StoredTweet;
import client.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class ProfileController {
    @FXML
    private Label headerNameLabel;

    @FXML
    private Label nameLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private VBox userTweetsContainer;

    @FXML
    private Label bioLabel;

    @FXML
    public void initialize() {
        refreshProfileData();
    }

    private void loadUserOwnTweets() {
        String username = UserSession.getInstance().getUsername();
        if (username == null) {
            username = "developer";
        }

        TweetStore.getInstance().seedIfEmpty();
        List<StoredTweet> personalPosts = TweetStore.getInstance().getTweetsByUsername(username);

        for (StoredTweet tweet : personalPosts) {
            renderPersonalTweetCard(tweet);
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

        Label timestamp = new Label("· " + tweet.getTimeAgo());
        timestamp.setFont(Font.font("System", 14));
        timestamp.setTextFill(Color.web("#71767b"));

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        Label bodyText = new Label(tweet.getContent());
        bodyText.setFont(Font.font("System", 15));
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setWrapText(true);
        bodyText.setMaxWidth(420);

        contentStack.getChildren().addAll(headerRow, bodyText);
        tweetRow.getChildren().addAll(avatarBox, contentStack);
        card.getChildren().add(tweetRow);

        userTweetsContainer.getChildren().add(card);
    }

    @FXML
    private void handleGoToHome() {
        NavigationManager.switchScene("/views/Feed.fxml");
    }

    @FXML
    private void handleLogout() {
        UserSession.getInstance().clearSession();
        TweetStore.getInstance().clear();
        NavigationManager.switchScene("/views/Login.fxml");
    }

    public void refreshProfileData() {
        shared.models.User currentUser = UserSession.getInstance().getCurrentUser();

        if (currentUser != null) {
            String activeDisplayName = currentUser.getDisplayName();
            String activeUsername = currentUser.getUsername();
            String activeBio = currentUser.getBio();

            nameLabel.setText(activeDisplayName != null ? activeDisplayName : "Active User");
            headerNameLabel.setText(activeDisplayName != null ? activeDisplayName : "Active User");
            usernameLabel.setText(activeUsername != null ? "@" + activeUsername : "@user");

            if (bioLabel != null) {
                bioLabel.setText(activeBio != null ? activeBio : "No bio available");
            }
        }
        else {
            nameLabel.setText("Active User");
            headerNameLabel.setText("Active User");
            usernameLabel.setText("@user");
        }

        userTweetsContainer.getChildren().clear();
        loadUserOwnTweets();
    }
}
