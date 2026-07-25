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

public class RegisterController {

    @FXML
    private TextField displayNameField;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button registerButton;

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
    private void handleRegister() {
        String displayName = displayNameField.getText() == null ? "" : displayNameField.getText().trim();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (displayName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please populate all fields before proceeding.");
            return;
        }

        if (password.length() < 8) {
            showError("Password must be at least 8 characters.");
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

        JsonObject registerPayload = new JsonObject();
        registerPayload.addProperty("displayName", displayName);
        registerPayload.addProperty("username", username);
        registerPayload.addProperty("email", email);
        registerPayload.addProperty("password", password);

        Request registerRequest = new Request(UUID.randomUUID().toString(), RequestType.REGISTER, registerPayload);

        if (registerButton != null) {
            registerButton.setDisable(true);
        }
        errorLabel.setStyle("-fx-text-fill: #71767b;");
        errorLabel.setText("Creating account...");

        Task<Response> task = new Task<>() {
            @Override
            protected Response call() throws Exception {
                return connection.sendMessage(registerRequest);
            }
        };

        task.setOnSucceeded(e -> {
            if (registerButton != null) {
                registerButton.setDisable(false);
            }
            handleRegisterResponse(task.getValue());
        });

        task.setOnFailed(e -> {
            if (registerButton != null) {
                registerButton.setDisable(false);
            }
            Throwable ex = task.getException();
            showError("Network transmission failed: " + (ex != null ? ex.getMessage() : "unknown error"));
        });

        Thread thread = new Thread(task, "register-request");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleRegisterResponse(Response response) {
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
            errorLabel.setText("Account created successfully! Welcome, " + user.getDisplayName() + "!");
            NavigationManager.switchScene("/views/Feed.fxml");
        } else if (response.getStatus() == StatusCode.CONFLICT) {
            showError("Username or email already exists.");
        } else if (response.getStatus() == StatusCode.BAD_REQUEST) {
            showError(response.getMessage() != null ? response.getMessage() : "Invalid registration details.");
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
    private void handleBackToLogin() {
        NavigationManager.switchScene("/views/Login.fxml");
    }
}