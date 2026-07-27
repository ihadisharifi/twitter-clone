package client.controllers;

import client.EmojiPickerHelper;
import client.NavigationManager;
import client.TweetMediaHelper;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.io.File;
import java.util.UUID;

public class ComposeController {

    @FXML private TextArea tweetTextArea;
    @FXML private Button cancelButton;
    @FXML private Button postButton;
    @FXML private Label avatarLabel;
    @FXML private Button emojiButton;
    @FXML private StackPane mediaPreviewContainer;
    @FXML private VBox mediaPreviewBox;

    private String selectedMediaPath;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

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
    private void handleEmojiClick() {
        EmojiPickerHelper.showEmojiPicker(emojiButton, tweetTextArea);
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
                    mediaPreviewBox.getChildren().setAll(previewNode);
                    mediaPreviewContainer.setVisible(true);
                    mediaPreviewContainer.setManaged(true);
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

        String content = tweetTextArea.getText() == null ? "" : tweetTextArea.getText().trim();
        if (content.isEmpty() && selectedMediaPath == null) {
            return;
        }

        String mediaPathToSend = selectedMediaPath;

        postButton.setDisable(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!connection.isConnected()) {
                    connection.connect();
                }

                JsonObject body = new JsonObject();
                String token = UserSession.getInstance().getToken();
                if (token != null) {
                    body.addProperty("token", token);
                }
                body.addProperty("content", content);
                if (mediaPathToSend != null) {
                    JsonArray mediaUrls = new JsonArray();
                    mediaUrls.add(mediaPathToSend);
                    body.add("mediaUrls", mediaUrls);
                }

                Request request = new Request(UUID.randomUUID().toString(), RequestType.CREATE_TWEET, body);
                Response response = connection.sendMessage(request);
                if (response.getStatus() != StatusCode.OK) {
                    throw new Exception(response.getMessage() != null ? response.getMessage() : "Failed to post tweet.");
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            postButton.setDisable(false);
            NavigationManager.switchScene("/views/Feed.fxml");
        });

        task.setOnFailed(e -> {
            postButton.setDisable(false);
            Throwable ex = task.getException();
            showError(ex != null ? ex.getMessage() : "Failed to post tweet.");
        });

        Thread thread = new Thread(task, "create-tweet");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.setHeaderText("Couldn't post tweet");
            alert.showAndWait();
        });
    }
}