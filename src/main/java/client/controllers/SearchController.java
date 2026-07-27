package client.controllers;

import client.LogoutHelper;
import client.NavigationManager;
import client.SideDrawerHelper;
import client.TweetMediaHelper;
import client.TweetTimeFormatter;
import client.UserSession;
import client.network.ServerConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import shared.models.Tweet;
import shared.models.User;
import shared.protocol.Request;
import shared.protocol.RequestType;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SearchController {

    @FXML private TextField searchInputField;
    @FXML private VBox searchResultsContainer;
    @FXML private HBox drawerOverlay;
    @FXML private VBox drawerPanel;
    @FXML private Label drawerDisplayName;
    @FXML private Label drawerUsername;

    private final Gson gson = new Gson();
    private final ServerConnection connection = ServerConnection.getInstance();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(1000));

    private int searchSequence;

    @FXML
    public void initialize() {
        SideDrawerHelper.populateUserHeader(drawerDisplayName, drawerUsername);

        searchDelay.setOnFinished(event -> search(searchInputField.getText()));
        searchInputField.textProperty().addListener((observable, oldValue, newValue) ->
                searchDelay.playFromStart()
        );

        showEmptyState("Search X", "Search users, tweets, or hashtags");
    }

    @FXML
    private void handleSearchAction() {
        searchDelay.stop();
        search(searchInputField.getText());
    }

    private void search(String rawQuery) {
        SearchTerm term = SearchTerm.from(rawQuery);

        if (term.value().isEmpty()) {
            searchSequence++;
            showEmptyState("Search X", "Search users, tweets, or hashtags");
            return;
        }

        int currentSequence = ++searchSequence;
        Task<SearchResults> task = createSearchTask(term);

        task.setOnSucceeded(event -> {
            if (currentSequence != searchSequence) {
                return;
            }
            showResults(term, task.getValue());
        });

        task.setOnFailed(event -> {
            if (currentSequence != searchSequence) {
                return;
            }
            showError(task.getException());
        });

        startTask(task, "search");
    }

    private Task<SearchResults> createSearchTask(SearchTerm term) {
        return new Task<>() {
            @Override
            protected SearchResults call() throws Exception {
                List<User> users = List.of();
                List<Tweet> tweets = List.of();

                if (term.type() != SearchType.HASHTAG) {
                    String userQuery = term.type() == SearchType.USERNAME
                            ? term.value()
                            : term.original();
                    users = searchUsers(userQuery);
                }

                if (term.type() != SearchType.USERNAME) {
                    tweets = searchTweets(term.original());
                }

                Map<Integer, User> usersById = new HashMap<>();
                for (User user : users) {
                    usersById.put(user.getId(), user);
                }

                return new SearchResults(users, tweets, usersById);
            }
        };
    }

    private List<User> searchUsers(String query) throws Exception {
        JsonObject body = authenticatedBody();
        body.addProperty("query", query);

        Response response = send(RequestType.SEARCH_USERS, body);
        User[] users = gson.fromJson(response.getPayload(), User[].class);
        return users == null ? List.of() : List.of(users);
    }

    private List<Tweet> searchTweets(String query) throws Exception {
        JsonObject body = authenticatedBody();
        body.addProperty("query", query);

        Response response = send(RequestType.SEARCH_TWEETS, body);
        Tweet[] tweets = gson.fromJson(response.getPayload(), Tweet[].class);
        return tweets == null ? List.of() : List.of(tweets);
    }

    private void showResults(SearchTerm term, SearchResults results) {
        searchResultsContainer.getChildren().clear();

        if (results.users().isEmpty() && results.tweets().isEmpty()) {
            showEmptyState(
                    "No results for \"" + term.original() + "\"",
                    "Try another user, word, or hashtag"
            );
            return;
        }

        if (!results.users().isEmpty()) {
            searchResultsContainer.getChildren().add(createSectionHeader("People"));
            for (User user : results.users()) {
                searchResultsContainer.getChildren().add(createUserCard(user));
            }
        }

        if (!results.tweets().isEmpty()) {
            String title = term.type() == SearchType.HASHTAG ? "Hashtag results" : "Tweets";
            searchResultsContainer.getChildren().add(createSectionHeader(title));
            for (Tweet tweet : results.tweets()) {
                User author = results.usersById().get(tweet.getAuthorId());
                searchResultsContainer.getChildren().add(createTweetCard(tweet, author));
            }
        }
    }

    private Node createUserCard(User user) {
        HBox card = new HBox(12);
        card.setStyle(
                "-fx-border-color: #333333; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-padding: 12 16; " +
                        "-fx-cursor: hand;"
        );

        Label avatar = new Label("👤");
        avatar.setFont(Font.font(22));

        VBox text = new VBox(2);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label name = new Label(displayName(user));
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label username = new Label("@" + username(user));
        username.setTextFill(Color.web("#71767b"));

        text.getChildren().addAll(name, username);

        if (user.getBio() != null && !user.getBio().isBlank()) {
            Label bio = new Label(user.getBio());
            bio.setTextFill(Color.web("#e7e9ea"));
            bio.setWrapText(true);
            text.getChildren().add(bio);
        }

        card.getChildren().addAll(avatar, text);
        card.setOnMouseClicked(event -> {
            ProfileController.pendingProfileUsername = user.getUsername();
            NavigationManager.switchScene("/views/Profile.fxml");
        });

        return card;
    }
    
    private Node createTweetCard(Tweet tweet, User author) {
        VBox card = new VBox(6);
        card.setStyle(
                "-fx-border-color: #333333; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-padding: 12 16;"
        );

        HBox header = new HBox(8);

        Label name = new Label(author != null ? displayName(author) : "User " + tweet.getAuthorId());
        name.setTextFill(Color.WHITE);
        name.setFont(Font.font("System", FontWeight.BOLD, 15));

        Label username = new Label(author != null
                ? "@" + username(author)
                : "@user" + tweet.getAuthorId());
        username.setTextFill(Color.web("#71767b"));

        Label timestamp = new Label(formatTime(tweet.getCreatedAt()));
        timestamp.setTextFill(Color.web("#71767b"));

        header.getChildren().addAll(name, username, timestamp);

        Label content = new Label(tweet.getContent() == null ? "" : tweet.getContent());
        content.setTextFill(Color.web("#e7e9ea"));
        content.setFont(Font.font(15));
        content.setWrapText(true);

        card.getChildren().addAll(header, content);

        if (tweet.getMedia() != null && !tweet.getMedia().isEmpty()) {
            String url = tweet.getMedia().get(0).getUrl();
            Node media = TweetMediaHelper.createMediaNode(url, 380, 220);
            if (media != null) {
                card.getChildren().add(media);
            }
        }

        return card;
    }

    private Label createSectionHeader(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 17));
        label.setStyle("-fx-padding: 14 16 8 16;");
        return label;
    }

    private void showEmptyState(String title, String subtitle) {
        searchResultsContainer.getChildren().clear();

        VBox box = new VBox(10);
        box.setStyle("-fx-padding: 60 40; -fx-alignment: center;");

        Label icon = new Label("🔍");
        icon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 22));

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setTextFill(Color.web("#71767b"));
        subtitleLabel.setWrapText(true);

        box.getChildren().addAll(icon, titleLabel, subtitleLabel);
        searchResultsContainer.getChildren().add(box);
    }

    private void showError(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "Search failed"
                : error.getMessage();
        showEmptyState("Search failed", message);
    }

    private JsonObject authenticatedBody() {
        JsonObject body = new JsonObject();
        String token = UserSession.getInstance().getToken();
        if (token != null && !token.isBlank()) {
            body.addProperty("token", token);
        }
        return body;
    }

    private Response send(RequestType type, JsonObject body) throws Exception {
        if (!connection.isConnected()) {
            connection.connect();
        }

        Request request = new Request(UUID.randomUUID().toString(), type, body);
        Response response = connection.sendMessage(request);

        if (response.getStatus() != StatusCode.OK) {
            throw new Exception(response.getMessage() == null
                    ? "Request failed"
                    : response.getMessage());
        }

        return response;
    }

    private void startTask(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private String displayName(User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? "User " + user.getId()
                : user.getDisplayName();
    }

    private String username(User user) {
        return user.getUsername() == null || user.getUsername().isBlank()
                ? "user" + user.getId()
                : user.getUsername();
    }

    private String formatTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return TweetTimeFormatter.formatFeedDot(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private enum SearchType {
        GENERAL,
        USERNAME,
        HASHTAG
    }

    private record SearchTerm(SearchType type, String original, String value) {
        private static SearchTerm from(String rawQuery) {
            String query = rawQuery == null ? "" : rawQuery.trim();

            if (query.startsWith("@")) {
                return new SearchTerm(
                        SearchType.USERNAME,
                        query,
                        query.substring(1).trim()
                );
            }

            if (query.startsWith("#")) {
                return new SearchTerm(
                        SearchType.HASHTAG,
                        query,
                        query.substring(1).trim()
                );
            }

            return new SearchTerm(SearchType.GENERAL, query, query);
        }
    }

    private record SearchResults(
            List<User> users,
            List<Tweet> tweets,
            Map<Integer, User> usersById
    ) {}

    @FXML private void handleOpenDrawer() {
        SideDrawerHelper.open(drawerOverlay, drawerPanel);
    }

    @FXML private void handleCloseDrawer() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML private void handleGoToHome() {
        NavigationManager.switchScene("/views/Feed.fxml");
    }

    @FXML private void handleGoToSearch() {
        SideDrawerHelper.close(drawerOverlay, drawerPanel);
    }

    @FXML private void handleCreatePost() {
        NavigationManager.switchScene("/views/Compose.fxml");
    }

    @FXML private void handleGoToProfile() {
        NavigationManager.switchScene("/views/Profile.fxml");
    }

    @FXML private void handleGoToBookmarks() {
        NavigationManager.switchScene("/views/Bookmarks.fxml");
    }

    @FXML private void handleLogout() {
        LogoutHelper.showConfirmation();
    }
}