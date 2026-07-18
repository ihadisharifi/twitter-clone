package client.controllers;

import client.NavigationManager;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.Instant;
import java.util.ArrayList;
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

    private final List<TimestampLabel> liveTimestamps = new ArrayList<>();
    private Timeline timeRefreshTimeline;

    @FXML
    public void initialize() {
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

        Label timestamp = createTimestampLabel(tweet.getCreatedAt());

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        // Profile only lists this user's posts — always offer delete
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button deleteButton = new Button("🗑");
        deleteButton.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #f4212e; -fx-padding: 0; "
                        + "-fx-cursor: hand; -fx-font-size: 14;"
        );
        deleteButton.setOnAction(event -> {
            String username = UserSession.getInstance().getUsername();
            if (username == null) {
                username = "developer";
            }
            if (TweetStore.getInstance().deleteTweet(tweet.getId(), username)) {
                refreshProfileData();
            }
        });
        headerRow.getChildren().addAll(spacer, deleteButton);

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
        liveTimestamps.clear();
        loadUserOwnTweets();
    }
}
