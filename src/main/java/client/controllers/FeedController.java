package client.controllers;

import client.NavigationManager;
import client.TweetStore;
import client.TweetStore.StoredTweet;
import client.UserSession;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class FeedController {

    @FXML
    private TextArea tweetTextArea;

    @FXML
    private VBox timelineContainer;

    private static final String STYLE_ACTION_IDLE =
            "-fx-background-color: transparent; -fx-text-fill: #71767b; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_REPLY_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_RETWEET_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #00ba7c; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_LIKE_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #f91880; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_COMPOSE_AREA =
            "-fx-control-inner-background: #000000; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #71767b; "
                    + "-fx-border-color: #333333; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String STYLE_POST_BTN =
            "-fx-background-color: #1d9bf0; -fx-text-fill: #ffffff; -fx-background-radius: 20; -fx-font-weight: bold;";

    @FXML
    public void initialize() {
        loadTimeline();
    }

    @FXML
    private void handlePostTweet() {
        String content = tweetTextArea.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        String username = currentUsername();
        String displayName = currentDisplayName();

        TweetStore.getInstance().addTweet(content, username, displayName);
        tweetTextArea.clear();
        loadTimeline();
    }

    private void loadTimeline() {
        timelineContainer.getChildren().clear();

        TweetStore store = TweetStore.getInstance();
        store.seedIfEmpty();

        for (StoredTweet tweet : store.getTimelineTweets()) {
            renderTweetCard(tweet, false);
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

        // Retweet / reply context line
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

        Label timestamp = new Label("· " + tweet.getTimeAgo());
        timestamp.setTextFill(Color.web("#71767b"));
        timestamp.setFont(Font.font("System", 14));

        headerRow.getChildren().addAll(displayName, userHandle, timestamp);

        Label bodyText = new Label(tweet.getContent());
        bodyText.setTextFill(Color.web("#e7e9ea"));
        bodyText.setFont(Font.font("System", 15));
        bodyText.setWrapText(true);
        bodyText.setMaxWidth(isNestedReply ? 360 : 420);

        contentStack.getChildren().addAll(headerRow, bodyText);

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

            actionToolbar.getChildren().addAll(replyButton, retweetButton, likeButton);
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

        HBox replyActions = new HBox(8);
        replyActions.setStyle("-fx-alignment: center-right;");
        Button sendReply = new Button("Reply");
        sendReply.setStyle(STYLE_POST_BTN);
        sendReply.setOnAction(e -> {
            String text = replyInput.getText() != null ? replyInput.getText().trim() : "";
            if (text.isEmpty()) {
                return;
            }
            TweetStore.getInstance().addReply(
                    parent.getId(),
                    text,
                    currentUsername(),
                    currentDisplayName()
            );
            applyReplyStyle(replyButton, parent);
            rebuildReplyPanel(replyPanel, parent, replyButton);
        });
        replyActions.getChildren().add(sendReply);

        replyPanel.getChildren().addAll(replyInput, replyActions);
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

        Label time = new Label("· " + reply.getTimeAgo());
        time.setTextFill(Color.web("#71767b"));
        time.setFont(Font.font("System", 13));

        header.getChildren().addAll(name, handle, time);

        Label body = new Label(reply.getContent());
        body.setTextFill(Color.web("#e7e9ea"));
        body.setFont(Font.font("System", 14));
        body.setWrapText(true);
        body.setMaxWidth(400);

        node.getChildren().addAll(header, body);
        return node;
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
    private void handleGoToProfile() {
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML
    private void handleLogout() {
        System.out.println("Invalidating active user context channel. Rerouting to login...");
        UserSession.getInstance().clearSession();
        TweetStore.getInstance().clear();
        NavigationManager.switchScene("/views/Login.fxml");
    }
}
