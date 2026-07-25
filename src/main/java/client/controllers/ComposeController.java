package client.controllers;

import client.NavigationManager;
import client.TweetStore;
import client.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class ComposeController {

    @FXML
    private TextArea tweetTextArea;

    @FXML
    private Button cancelButton;

    @FXML
    private Button postButton;

    @FXML
    private Label avatarLabel;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            if (tweetTextArea != null) {
                tweetTextArea.requestFocus();
            }
        });
    }

    @FXML
    private void handleCancel() {
        NavigationManager.switchScene("/views/Feed.fxml");
    }

    @FXML
    private void handlePost() {
        if (tweetTextArea == null) {
            return;
        }

        String content = tweetTextArea.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        String username = UserSession.getInstance().getUsername();
        if (username == null || username.isBlank()) {
            username = "developer";
        }

        String displayName = UserSession.getInstance().getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "Guest";
        }

        TweetStore.getInstance().addTweet(content, username, displayName);
        NavigationManager.switchScene("/views/Feed.fxml");
    }
}
