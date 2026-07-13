package server.database;

import shared.models.Hashtag;
import shared.models.Media;
import shared.models.Session;
import shared.models.Tweet;
import shared.models.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TempDataStore {

    private static final TempDataStore INSTANCE = new TempDataStore();
    public static TempDataStore get() { return INSTANCE; }
    private TempDataStore() {}

    private final AtomicInteger userIdCounter = new AtomicInteger(1);
    private final AtomicInteger tweetIdCounter = new AtomicInteger(1);
    private final AtomicInteger hashtagIdCounter = new AtomicInteger(1);
    private final AtomicInteger mediaIdCounter = new AtomicInteger(1);

    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Integer> userIdByUsername = new ConcurrentHashMap<>();
    private final Map<String, Integer> userIdByEmail = new ConcurrentHashMap<>();
    private final Map<Integer, String> passwordHashByUserId = new ConcurrentHashMap<>();

    private final Map<String, Session> sessionsByToken = new ConcurrentHashMap<>();

    private final Map<Integer, Tweet> tweetsById = new ConcurrentHashMap<>();

    private final Map<Integer, Set<Integer>> followersByUser = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Integer>> followingByUser = new ConcurrentHashMap<>();

    private final Map<Integer, Set<Integer>> likesByTweet = new ConcurrentHashMap<>();

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");
    private final Map<String, Hashtag> hashtagByName = new ConcurrentHashMap<>();
    private final Map<Integer, List<Hashtag>> hashtagsByTweetId = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> tweetIdsByHashtag = new ConcurrentHashMap<>();

    private final Map<Integer, List<Media>> mediaByTweetId = new ConcurrentHashMap<>();


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

    public List<User> searchUsersByQuery(String query) {
        String q = query.toLowerCase();
        List<User> result = new ArrayList<>();
        for (User u : usersById.values()) {
            if (u.getUsername().toLowerCase().contains(q) || u.getDisplayName().toLowerCase().contains(q)) {
                result.add(u);
            }
        }
        return result;
    }


    public Session createSession(int userId) {
        String token = UUID.randomUUID().toString();
        Session session = new Session(0, userId, token, Instant.now().plusSeconds(60L * 60 * 24).toString());
        sessionsByToken.put(token, session);
        return session;
    }

    public Session getSession(String token) {
        if (token == null) return null;
        Session session = sessionsByToken.get(token);
        if (session == null) return null;
        if (Instant.parse(session.getExpiresAt()).isBefore(Instant.now())) {
            sessionsByToken.remove(token);
            return null;
        }
        return session;
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

    public List<Tweet> getRepliesTo(int tweetId) {
        List<Tweet> list = new ArrayList<>();
        for (Tweet t : tweetsById.values()) {
            if (t.getReplyToId() != null && t.getReplyToId() == tweetId) list.add(t);
        }
        list.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        return list;
    }

    public List<Tweet> searchTweetsByKeyword(String query) {
        String q = query.toLowerCase();
        List<Tweet> result = new ArrayList<>();
        for (Tweet t : tweetsById.values()) {
            if (t.getContent() != null && t.getContent().toLowerCase().contains(q)) {
                result.add(t);
            }
        }
        result.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return result;
    }


    public boolean follow(int followerId, int followingId) {
        if (followerId == followingId) return false;
        followingByUser.computeIfAbsent(followerId, k -> ConcurrentHashMap.newKeySet()).add(followingId);
        followersByUser.computeIfAbsent(followingId, k -> ConcurrentHashMap.newKeySet()).add(followerId);
        return true;
    }

    public boolean unfollow(int followerId, int followingId) {
        Set<Integer> following = followingByUser.get(followerId);
        if (following != null) following.remove(followingId);
        Set<Integer> followers = followersByUser.get(followingId);
        if (followers != null) followers.remove(followerId);
        return true;
    }

    public boolean isFollowing(int followerId, int followingId) {
        Set<Integer> set = followingByUser.get(followerId);
        return set != null && set.contains(followingId);
    }

    public Set<Integer> getFollowerIds(int userId) {
        return followersByUser.getOrDefault(userId, Set.of());
    }

    public Set<Integer> getFollowingIds(int userId) {
        return followingByUser.getOrDefault(userId, Set.of());
    }



    public boolean likeTweet(int userId, int tweetId) {
        return likesByTweet.computeIfAbsent(tweetId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public boolean unlikeTweet(int userId, int tweetId) {
        Set<Integer> set = likesByTweet.get(tweetId);
        return set != null && set.remove(userId);
    }

    public boolean isLikedByUser(int userId, int tweetId) {
        Set<Integer> set = likesByTweet.get(tweetId);
        return set != null && set.contains(userId);
    }

    public int getLikeCount(int tweetId) {
        Set<Integer> set = likesByTweet.get(tweetId);
        return set == null ? 0 : set.size();
    }


    public List<Hashtag> extractAndStoreHashtags(int tweetId, String content) {
        List<Hashtag> tags = new ArrayList<>();
        if (content != null) {
            Matcher m = HASHTAG_PATTERN.matcher(content);
            while (m.find()) {
                String name = m.group(1).toLowerCase();
                Hashtag tag = hashtagByName.computeIfAbsent(name, n -> new Hashtag(hashtagIdCounter.getAndIncrement(), n));
                if (!tags.contains(tag)) tags.add(tag);
                tweetIdsByHashtag.computeIfAbsent(name, n -> ConcurrentHashMap.newKeySet()).add(tweetId);
            }
        }
        hashtagsByTweetId.put(tweetId, tags);
        return tags;
    }

    public List<Hashtag> getHashtagsForTweet(int tweetId) {
        return hashtagsByTweetId.getOrDefault(tweetId, new ArrayList<>());
    }

    public List<Tweet> getTweetsByHashtag(String tagName) {
        Set<Integer> ids = tweetIdsByHashtag.getOrDefault(tagName.toLowerCase(), Set.of());
        List<Tweet> list = new ArrayList<>();
        for (Integer id : ids) {
            Tweet t = tweetsById.get(id);
            if (t != null) list.add(t);
        }
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }


    public List<Media> attachMedia(int tweetId, List<String> urls) {
        List<Media> list = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                list.add(new Media(mediaIdCounter.getAndIncrement(), tweetId, url));
            }
        }
        mediaByTweetId.put(tweetId, list);
        return list;
    }

    public List<Media> getMedia(int tweetId) {
        return mediaByTweetId.getOrDefault(tweetId, new ArrayList<>());
    }
}
