package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import shared.models.User;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.UUID;

public class BookmarksController {

    @FXML private VBox bookmarksContainer;
    @FXML private HBox drawerOverlay;
    @FXML private VBox drawerPanel;
    @FXML private Label drawerDisplayName;
    @FXML private Label drawerUsername;
    @FXML private Label drawerFollowingCount;
    @FXML private Label drawerFollowersCount;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
        renderUnsupportedState();
        loadDrawerProfile();
    }

    private void renderUnsupportedState() {
        bookmarksContainer.getChildren().clear();
        VBox box = new VBox(8);
        box.setStyle("-fx-padding: 80 40; -fx-alignment: center;");

        Label title = new Label("Bookmarks aren't available yet");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label detail = new Label(
                "The server database does not currently provide bookmark storage. "
                        + "No fake or device-only bookmarks are being shown."
        );
        detail.setTextFill(Color.web("#71767b"));
        detail.setFont(Font.font("System", 14));
        detail.setWrapText(true);
        detail.setMaxWidth(420);
        box.getChildren().addAll(title, detail);
        bookmarksContainer.getChildren().add(box);
    }

    private void loadDrawerProfile() {
        Task<JsonObject> task = new Task<>() {
            @Override
            protected JsonObject call() throws Exception {
                String token = UserSession.getInstance().getToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("Your session has expired.");
                }
                if (!connection.isConnected()) {
                    connection.connect();
                }
                JsonObject body = new JsonObject();
                body.addProperty("token", token);
                Response response = connection.sendMessage(new Request(
                        UUID.randomUUID().toString(), RequestType.GET_PROFILE, body
                ));
                if (response == null || response.getStatus() != StatusCode.OK
                        || response.getPayload() == null || !response.getPayload().isJsonObject()) {
                    throw new IllegalStateException("Unable to load profile.");
                }
                return response.getPayload().getAsJsonObject();
            }
        };
        task.setOnSucceeded(event -> {
            JsonObject payload = task.getValue();
            User user = gson.fromJson(payload.get("user"), User.class);
            if (user != null) {
                User current = UserSession.getInstance().getCurrentUser();
                if (current != null) {
                    current.setDisplayName(user.getDisplayName());
                    current.setBio(user.getBio());
                    current.setAvatarUrl(user.getAvatarUrl());
                    current.setBannerUrl(user.getBannerUrl());
                }
                SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
            }
            drawerFollowingCount.setText(number(payload, "followingCount"));
            drawerFollowersCount.setText(number(payload, "followersCount"));
        });
        start(task, "load-bookmarks-drawer-profile");
    }

    private String number(JsonObject payload, String property) {
        return payload.has(property) && !payload.get(property).isJsonNull()
                ? String.valueOf(payload.get(property).getAsInt()) : "0";
    }

    private void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML private void handleOpenDrawer() {
        loadDrawerProfile();
        SideDrawerHelper.open(drawerOverlay, drawerPanel);
    }
    @FXML private void handleCloseDrawer() { SideDrawerHelper.close(drawerOverlay, drawerPanel); }
    @FXML private void handleBack() { NavigationManager.switchScene("/views/Feed.fxml"); }
    @FXML private void handleGoToHome() { NavigationManager.switchScene("/views/Feed.fxml"); }
    @FXML private void handleGoToSearch() { NavigationManager.switchScene("/views/Search.fxml"); }
    @FXML private void handleCreatePost() { NavigationManager.switchScene("/views/Compose.fxml"); }
    @FXML private void handleGoToProfile() { NavigationManager.switchScene("/views/Profile.fxml"); }
    @FXML private void handleGoToBookmarks() { SideDrawerHelper.close(drawerOverlay, drawerPanel); }
    @FXML private void handleLogout() { LogoutHelper.showConfirmation(); }
}
