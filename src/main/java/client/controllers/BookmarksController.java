package client.controllers;

import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetStore;
import client.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class BookmarksController {

    @FXML
    private HBox drawerOverlay;

    @FXML
    private VBox drawerPanel;

    @FXML
    private Label drawerDisplayName;

    @FXML
    private Label drawerUsername;

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);
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
    private void handleGoToProfile() {
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML
    private void handleGoToBookmarks() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML
    private void handleLogout() {
        UserSession.getInstance().clearSession();
        TweetStore.getInstance().clear();
        NavigationManager.switchScene("/views/Login.fxml");
    }
}
