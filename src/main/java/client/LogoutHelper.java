package client;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import java.util.Optional;

public final class LogoutHelper {

    private LogoutHelper() {}

    public static void showConfirmation() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Log out");
        alert.setHeaderText("Log out of X?");
        alert.setContentText("You can always log back in at any time.");

        ButtonType cancelButton = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        ButtonType logoutButton = new ButtonType("Log out", ButtonData.OK_DONE);

        alert.getButtonTypes().setAll(cancelButton, logoutButton);

        try {
            DialogPane dialogPane = alert.getDialogPane();
            if (LogoutHelper.class.getResource("/styles/twitter.css") != null) {
                dialogPane.getStylesheets().add(LogoutHelper.class.getResource("/styles/twitter.css").toExternalForm());
            }
            dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #333333; -fx-border-width: 1px;");
        } catch (Exception e) {
            // Ignore optional styling faults to guarantee dialog functionality
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == logoutButton) {
            System.out.println("Invalidating active user context channel. Rerouting to login...");
            UserSession.getInstance().clearSession();
            NavigationManager.switchScene("/views/Login.fxml");
        }
    }
}
