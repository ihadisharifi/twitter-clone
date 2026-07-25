package client.controllers;

import client.NavigationManager;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import shared.models.Session;
import shared.models.User;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.util.UUID;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private final ServerConnection connection = ServerConnection.getInstance();
    private final Gson gson = new Gson();

    @FXML
    public void initialize() {
        try {
            connection.connect();
        } catch (Exception e) {
            errorLabel.setStyle("-fx-text-fill: #f4212e;");
            errorLabel.setText("Network Error: Could not connect to backend server.");
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        if (!connection.isConnected()) {
            try {
                connection.connect();
            } catch (Exception e) {
                showError("Network Error: Could not connect to backend server.");
                return;
            }
        }

        JsonObject loginCredentials = new JsonObject();
        loginCredentials.addProperty("username", username);
        loginCredentials.addProperty("password", password);

        Request loginRequest = new Request(UUID.randomUUID().toString(), RequestType.LOGIN, loginCredentials);

        loginButton.setDisable(true);
        errorLabel.setStyle("-fx-text-fill: #71767b;");
        errorLabel.setText("Signing in...");

        Task<Response> task = new Task<>() {
            @Override
            protected Response call() throws Exception {
                return connection.sendMessage(loginRequest);
            }
        };

        task.setOnSucceeded(e -> {
            loginButton.setDisable(false);
            handleLoginResponse(task.getValue());
        });

        task.setOnFailed(e -> {
            loginButton.setDisable(false);
            Throwable ex = task.getException();
            showError("Transmission failed: " + (ex != null ? ex.getMessage() : "unknown error"));
        });

        Thread thread = new Thread(task, "login-request");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleLoginResponse(Response response) {
        if (response == null) {
            showError("No response from server.");
            return;
        }

        if (response.getStatus() == StatusCode.OK) {
            JsonObject result = response.getPayload().getAsJsonObject();
            User user = gson.fromJson(result.get("user"), User.class);
            String token = result.get("token").getAsString();
            Session session = new Session(0, user.getId(), token, null);

            UserSession.getInstance().startSession(user, session);

            errorLabel.setStyle("-fx-text-fill: #00ba7c;");
            errorLabel.setText("Login successful! Redirecting...");
            NavigationManager.switchScene("/views/Feed.fxml");
        } else if (response.getStatus() == StatusCode.UNAUTHORIZED) {
            showError("Invalid username or password.");
        } else if (response.getStatus() == StatusCode.NOT_FOUND) {
            showError("Account not found.");
        } else {
            showError("Server error: " + response.getMessage());
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setStyle("-fx-text-fill: #f4212e;");
            errorLabel.setText(message);
        });
    }

    @FXML
    private void handleGoToRegister() {
        NavigationManager.switchScene("/views/Register.fxml");
    }
}