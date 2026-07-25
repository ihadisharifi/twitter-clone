package client;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import shared.models.User;

/**
 * Shared open/close + user-header wiring for the mobile-style left drawer
 * used on Home, Profile, and Bookmarks.
 */
public final class SideDrawerHelper {

    private static final double DRAWER_WIDTH = 300.0;
    private static final Duration ANIM_DURATION = Duration.millis(180);

    private SideDrawerHelper() {}

    public static void populateUserHeader(Label displayNameLabel, Label usernameLabel) {
        User user = UserSession.getInstance().getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String username = user.getUsername();
            displayNameLabel.setText(name != null && !name.isBlank() ? name : "User");
            usernameLabel.setText(username != null && !username.isBlank() ? "@" + username : "@user");
        } else {
            displayNameLabel.setText("Guest");
            usernameLabel.setText("@guest");
        }
    }

    public static void open(HBox overlay, VBox panel) {
        if (overlay == null || panel == null) {
            return;
        }
        overlay.setManaged(true);
        overlay.setVisible(true);
        overlay.setMouseTransparent(false);

        panel.setTranslateX(-DRAWER_WIDTH);
        TranslateTransition slideIn = new TranslateTransition(ANIM_DURATION, panel);
        slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);
        slideIn.play();
    }

    public static void close(HBox overlay, VBox panel) {
        if (overlay == null || panel == null || !overlay.isVisible()) {
            return;
        }
        TranslateTransition slideOut = new TranslateTransition(ANIM_DURATION, panel);
        slideOut.setToX(-DRAWER_WIDTH);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        slideOut.setOnFinished(e -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
            overlay.setMouseTransparent(true);
            panel.setTranslateX(0);
        });
        slideOut.play();
    }
}
