package client.controllers;

import client.NavigationManager;
import client.TweetMediaHelper;
import client.TweetStore;
import client.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private VBox mediaPreviewBox;

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
                new FileChooser.ExtensionFilter("Media Files (*.png, *.jpg, *.jpeg, *.gif, *.mp4, *.m4v)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.mp4", "*.m4v"),
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.m4v")
        );
        Stage stage = (Stage) tweetTextArea.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            selectedMediaPath = file.toURI().toString();
            try {
                Node previewNode = TweetMediaHelper.createMediaNode(selectedMediaPath, 380, 200);
                if (previewNode != null) {
                    if (mediaPreviewBox != null) {
                        mediaPreviewBox.getChildren().clear();
                        mediaPreviewBox.getChildren().add(previewNode);
                    }
                    if (mediaPreviewContainer != null) {
                        mediaPreviewContainer.setVisible(true);
                        mediaPreviewContainer.setManaged(true);
                    }
                } else {
                    handleRemoveMedia();
                }
            } catch (Exception e) {
                handleRemoveMedia();
            }
        }
    }

    @FXML
    private void handleRemoveMedia() {
        selectedMediaPath = null;
        if (mediaPreviewBox != null) {
            mediaPreviewBox.getChildren().clear();
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
