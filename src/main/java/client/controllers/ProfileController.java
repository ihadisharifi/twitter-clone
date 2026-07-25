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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
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
    private static final String STYLE_RETWEET_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #00ba7c; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_LIKE_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #f91880; -fx-padding: 0; -fx-cursor: hand;";
    private static final String STYLE_BOOKMARK_ACTIVE =
            "-fx-background-color: transparent; -fx-text-fill: #1d9bf0; -fx-padding: 0; -fx-cursor: hand;";

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
            refreshProfileData();
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

        userTweetsContainer.getChildren().add(card);
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
            try {
                String uriString;
                if (mediaPath.startsWith("file:") || mediaPath.startsWith("http:") || mediaPath.startsWith("https:")) {
                    uriString = mediaPath;
                } else {
                    java.io.File file = new java.io.File(mediaPath);
                    uriString = file.exists() ? file.toURI().toString() : null;
                }

                if (uriString != null) {
                    Image mediaImg = new Image(uriString, 420, 260, true, true);
                    if (!mediaImg.isError()) {
                        ImageView mediaView = new ImageView(mediaImg);
                        mediaView.setFitWidth(380);
                        mediaView.setFitHeight(220);
                        mediaView.setPreserveRatio(true);

                        Rectangle clip = new Rectangle();
                        clip.setArcWidth(16);
                        clip.setArcHeight(16);
                        clip.widthProperty().bind(mediaView.layoutBoundsProperty().map(b -> b.getWidth()));
                        clip.heightProperty().bind(mediaView.layoutBoundsProperty().map(b -> b.getHeight()));
                        mediaView.setClip(clip);

                        contentStack.getChildren().add(mediaView);
                    }
                }
            } catch (Exception ignored) {}
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
