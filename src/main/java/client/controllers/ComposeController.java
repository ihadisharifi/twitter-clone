package client.controllers;

import client.NavigationManager;
import client.TweetStore;
import client.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

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
    private StackPane mediaPreviewContainer;

    @FXML
    private ImageView mediaPreviewImageView;

    private String selectedMediaPath;

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
    private void handleAttachMedia() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Attach Media");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files (*.png, *.jpg, *.jpeg, *.webp, *.mp4)", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.mp4"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("Video Files", "*.mp4")
        );
        Stage stage = (Stage) tweetTextArea.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            selectedMediaPath = file.toURI().toString();
            try {
                Image img = new Image(selectedMediaPath, 380, 200, true, true);
                if (!img.isError()) {
                    if (mediaPreviewImageView != null) {
                        mediaPreviewImageView.setImage(img);
                    }
                    if (mediaPreviewContainer != null) {
                        mediaPreviewContainer.setVisible(true);
                        mediaPreviewContainer.setManaged(true);
                    }
                }
            } catch (Exception e) {
                handleRemoveMedia();
            }
        }
    }

    @FXML
    private void handleRemoveMedia() {
        selectedMediaPath = null;
        if (mediaPreviewImageView != null) {
            mediaPreviewImageView.setImage(null);
        }
        if (mediaPreviewContainer != null) {
            mediaPreviewContainer.setVisible(false);
            mediaPreviewContainer.setManaged(false);
        }
    }

    @FXML
    private void handlePost() {
        if (tweetTextArea == null) {
            return;
        }

        String content = tweetTextArea.getText().trim();
        if (content.isEmpty() && selectedMediaPath == null) {
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

        TweetStore.getInstance().addTweet(content, username, displayName, selectedMediaPath);
        NavigationManager.switchScene("/views/Feed.fxml");
    }
}
