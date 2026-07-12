package server.database;

import shared.models.Session;
import shared.models.Tweet;
import shared.models.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TempDataStore {

    private static final TempDataStore INSTANCE = new TempDataStore();
    public static TempDataStore get() { return INSTANCE; }
    private TempDataStore() {}

    private final AtomicInteger userIdCounter = new AtomicInteger(1);
    private final AtomicInteger tweetIdCounter = new AtomicInteger(1);

    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Integer> userIdByUsername = new ConcurrentHashMap<>(); // lowercase
    private final Map<String, Integer> userIdByEmail = new ConcurrentHashMap<>();    // lowercase
    private final Map<Integer, String> passwordHashByUserId = new ConcurrentHashMap<>();

    private final Map<String, Session> sessionsByToken = new ConcurrentHashMap<>();

    private final Map<Integer, Tweet> tweetsById = new ConcurrentHashMap<>();


    public boolean usernameTaken(String username) {
        return userIdByUsername.containsKey(username.toLowerCase());
    }

    public boolean emailTaken(String email) {
        return userIdByEmail.containsKey(email.toLowerCase());
    }

    public User createUser(String username, String email, String displayName, String passwordHash) {
        int id = userIdCounter.getAndIncrement();
        User user = new User(id, username, email, displayName, "", null, null, Instant.now().toString());
        usersById.put(id, user);
        userIdByUsername.put(username.toLowerCase(), id);
        userIdByEmail.put(email.toLowerCase(), id);
        passwordHashByUserId.put(id, passwordHash);
        return user;
    }

    public User getUserById(int id) {
        return usersById.get(id);
    }

    public User getUserByUsername(String username) {
        Integer id = userIdByUsername.get(username.toLowerCase());
        return id == null ? null : usersById.get(id);
    }

    public String getPasswordHash(int userId) {
        return passwordHashByUserId.get(userId);
    }


    public Session createSession(int userId) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(0, userId, token, Instant.now().plusSeconds(60L * 60 * 24).toString());
        sessionsByToken.put(token, session);
        return session;
    }

    public Session getSession(String token) {
        return token == null ? null : sessionsByToken.get(token);
    }

    public void deleteSession(String token) {
        if (token != null) sessionsByToken.remove(token);
    }


    public Tweet createTweet(int authorId, String content, Integer replyToId, Integer retweetToId) {
        int id = tweetIdCounter.getAndIncrement();
        Tweet tweet = new Tweet(id, authorId, content, replyToId, retweetToId, Instant.now().toString());
        tweetsById.put(id, tweet);
        return tweet;
    }

    public Tweet getTweet(int id) {
        return tweetsById.get(id);
    }

    public boolean deleteTweet(int id, int requesterId) {
        Tweet tweet = tweetsById.get(id);
        if (tweet == null || tweet.getAuthorId() != requesterId) return false;
        tweetsById.remove(id);
        return true;
    }

    public List<Tweet> getTweetsByAuthor(int authorId) {
        List<Tweet> list = new ArrayList<>();
        for (Tweet t : tweetsById.values()) {
            if (t.getAuthorId() == authorId) list.add(t);
        }
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }
}