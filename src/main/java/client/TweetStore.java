package client;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory tweet store that survives scene switches.
 * Supports original posts, retweets (reposted by the current user), and replies.
 * Each entry stores a real {@link Instant} creation time for Twitter-style relative labels.
 */
public class TweetStore {

    private static TweetStore instance;

    private final List<StoredTweet> tweets = new ArrayList<>();
    private int nextId = 1;
    private boolean seeded;

    private TweetStore() {}

    public static synchronized TweetStore getInstance() {
        if (instance == null) {
            instance = new TweetStore();
        }
        return instance;
    }

    public synchronized void seedIfEmpty() {
        if (seeded || !tweets.isEmpty()) {
            return;
        }
        seeded = true;
        Instant now = Instant.now();
        addTweetInternal(
                "Just deployed the new centralized Navigation Pipeline! Everything feels smooth. #JavaFX #XClone",
                "developer",
                "Guest",
                now.minus(2, ChronoUnit.HOURS),
                null,
                null,
                null,
                null
        );
        addTweetInternal(
                "Designing atomic layouts with inline CSS components is highly efficient for dark themes.",
                "developer",
                "Guest",
                now.minus(1, ChronoUnit.DAYS),
                null,
                null,
                null,
                null
        );
    }

    /** Original post (not a retweet card). */
    public synchronized StoredTweet addTweet(String content, String username, String displayName) {
        return addTweetInternal(content, username, displayName, Instant.now(), null, null, null, null);
    }

    /**
     * Creates a reply under {@code parentId}. Increments the parent's reply count.
     * Returns null if the parent does not exist.
     */
    public synchronized StoredTweet addReply(int parentId, String content, String username, String displayName) {
        StoredTweet parent = findById(parentId);
        if (parent == null || content == null || content.isBlank()) {
            return null;
        }
        // Always attach replies to the original post (not to a retweet card)
        StoredTweet root = resolveOriginal(parent);
        StoredTweet reply = addTweetInternal(
                content.trim(),
                username != null ? username : "user",
                displayName != null ? displayName : "User",
                Instant.now(),
                root.getId(),
                null,
                root.getAuthorUsername(),
                root.getAuthorDisplayName()
        );
        root.incrementReplies();
        return reply;
    }

    /**
     * Creates a retweet card authored by the current user that re-shares the original post.
     * Toggles off if the user already retweeted it (removes their retweet card).
     *
     * @return true if now retweeted, false if un-retweeted or failed
     */
    public synchronized boolean toggleRetweet(int originalOrAnyId, String username, String displayName) {
        StoredTweet source = findById(originalOrAnyId);
        if (source == null) {
            return false;
        }
        StoredTweet original = resolveOriginal(source);
        if (username == null) {
            username = "user";
        }
        if (displayName == null) {
            displayName = "User";
        }

        StoredTweet existing = findUserRetweetOf(original.getId(), username);
        if (existing != null) {
            tweets.remove(existing);
            original.decrementRetweets();
            original.setRetweetedByCurrentUser(false);
            return false;
        }

        // Retweet card: authored by current user, content mirrors original
        addTweetInternal(
                original.getContent(),
                username,
                displayName,
                Instant.now(),
                null,
                original.getId(),
                original.getAuthorUsername(),
                original.getAuthorDisplayName()
        );
        original.incrementRetweets();
        original.setRetweetedByCurrentUser(true);
        return true;
    }

    public synchronized boolean toggleLike(int tweetId) {
        StoredTweet tweet = findById(tweetId);
        if (tweet == null) {
            return false;
        }
        // Likes apply to the original content for retweet cards
        StoredTweet target = resolveOriginal(tweet);
        return target.toggleLike();
    }

    /**
     * Deletes a tweet owned by {@code username}.
     * <ul>
     *   <li>Original post → removes its replies and all retweet cards of it</li>
     *   <li>Reply → removes the reply and decrements the parent reply count</li>
     *   <li>Retweet card → removes only that repost and decrements the original count</li>
     * </ul>
     *
     * @return true if something was deleted
     */
    public synchronized boolean deleteTweet(int tweetId, String username) {
        StoredTweet tweet = findById(tweetId);
        if (tweet == null || username == null) {
            return false;
        }
        if (!username.equals(tweet.getAuthorUsername())) {
            return false; // only the author can delete
        }

        if (tweet.isRetweet()) {
            StoredTweet original = tweet.getRetweetOfId() != null
                    ? findById(tweet.getRetweetOfId())
                    : null;
            tweets.remove(tweet);
            if (original != null) {
                original.decrementRetweets();
                if (username.equals(tweet.getAuthorUsername())) {
                    original.setRetweetedByCurrentUser(false);
                }
            }
            return true;
        }

        if (tweet.isReply()) {
            StoredTweet parent = tweet.getReplyToId() != null
                    ? findById(tweet.getReplyToId())
                    : null;
            tweets.remove(tweet);
            if (parent != null) {
                parent.decrementReplies();
            }
            return true;
        }

        // Original root post: cascade delete replies + retweet cards
        int id = tweet.getId();
        tweets.removeIf(t ->
                t.getId() == id
                        || (t.isReply() && t.getReplyToId() != null && t.getReplyToId() == id)
                        || (t.isRetweet() && t.getRetweetOfId() != null && t.getRetweetOfId() == id)
        );
        return true;
    }

    public synchronized StoredTweet findById(int id) {
        for (StoredTweet tweet : tweets) {
            if (tweet.getId() == id) {
                return tweet;
            }
        }
        return null;
    }

    /**
     * Timeline roots: original posts + retweet cards. Pure replies are nested under parents.
     * Newest first.
     */
    public synchronized List<StoredTweet> getTimelineTweets() {
        List<StoredTweet> result = new ArrayList<>();
        for (StoredTweet tweet : tweets) {
            if (!tweet.isReply()) {
                result.add(tweet);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Direct replies to a parent (or its original), oldest first for thread reading. */
    public synchronized List<StoredTweet> getReplies(int parentId) {
        StoredTweet parent = findById(parentId);
        if (parent == null) {
            return List.of();
        }
        int rootId = resolveOriginal(parent).getId();
        List<StoredTweet> result = new ArrayList<>();
        // tweets list is newest-first; walk reverse so replies show oldest → newest
        for (int i = tweets.size() - 1; i >= 0; i--) {
            StoredTweet tweet = tweets.get(i);
            if (tweet.isReply() && tweet.getReplyToId() != null && tweet.getReplyToId() == rootId) {
                result.add(tweet);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Profile feed: posts + retweets + replies authored by this username.
     * Newest first.
     */
    public synchronized List<StoredTweet> getTweetsByUsername(String username) {
        if (username == null) {
            return List.of();
        }
        List<StoredTweet> result = new ArrayList<>();
        for (StoredTweet tweet : tweets) {
            if (username.equals(tweet.getAuthorUsername())) {
                result.add(tweet);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Alias for timeline roots; prefer {@link #getTimelineTweets()}. */
    public synchronized List<StoredTweet> getAllTweets() {
        return getTimelineTweets();
    }

    public synchronized void clear() {
        tweets.clear();
        seeded = false;
        nextId = 1;
    }

    private StoredTweet resolveOriginal(StoredTweet tweet) {
        if (tweet.isRetweet() && tweet.getRetweetOfId() != null) {
            StoredTweet original = findById(tweet.getRetweetOfId());
            if (original != null) {
                return original;
            }
        }
        return tweet;
    }

    private StoredTweet findUserRetweetOf(int originalId, String username) {
        for (StoredTweet tweet : tweets) {
            if (tweet.isRetweet()
                    && tweet.getRetweetOfId() != null
                    && tweet.getRetweetOfId() == originalId
                    && username.equals(tweet.getAuthorUsername())) {
                return tweet;
            }
        }
        return null;
    }

    private StoredTweet addTweetInternal(
            String content,
            String username,
            String displayName,
            Instant createdAt,
            Integer replyToId,
            Integer retweetOfId,
            String originalAuthorUsername,
            String originalAuthorDisplayName
    ) {
        StoredTweet tweet = new StoredTweet(
                nextId++,
                content,
                username != null ? username : "user",
                displayName != null ? displayName : "User",
                createdAt != null ? createdAt : Instant.now(),
                replyToId,
                retweetOfId,
                originalAuthorUsername,
                originalAuthorDisplayName
        );
        tweets.add(0, tweet);
        return tweet;
    }

    public static final class StoredTweet {
        private final int id;
        private final String content;
        private final String authorUsername;
        private final String authorDisplayName;
        private final Instant createdAt;

        /** Parent original tweet id when this is a reply. */
        private final Integer replyToId;
        /** Original tweet id when this is a retweet card. */
        private final Integer retweetOfId;
        /** For replies/retweets: who wrote the original shared post. */
        private final String originalAuthorUsername;
        private final String originalAuthorDisplayName;

        private int likes;
        private int replies;
        private int retweets;
        private boolean likedByCurrentUser;
        private boolean retweetedByCurrentUser;

        public StoredTweet(
                int id,
                String content,
                String authorUsername,
                String authorDisplayName,
                Instant createdAt,
                Integer replyToId,
                Integer retweetOfId,
                String originalAuthorUsername,
                String originalAuthorDisplayName
        ) {
            this.id = id;
            this.content = content;
            this.authorUsername = authorUsername;
            this.authorDisplayName = authorDisplayName;
            this.createdAt = createdAt != null ? createdAt : Instant.now();
            this.replyToId = replyToId;
            this.retweetOfId = retweetOfId;
            this.originalAuthorUsername = originalAuthorUsername;
            this.originalAuthorDisplayName = originalAuthorDisplayName;
        }

        public int getId() { return id; }
        public String getContent() { return content; }
        public String getAuthorUsername() { return authorUsername; }
        public String getAuthorDisplayName() { return authorDisplayName; }
        public Instant getCreatedAt() { return createdAt; }

        /** Live Twitter-style relative label (recomputed from {@link #createdAt}). */
        public String getTimeAgo() {
            return TweetTimeFormatter.formatRelative(createdAt);
        }

        public Integer getReplyToId() { return replyToId; }
        public Integer getRetweetOfId() { return retweetOfId; }
        public String getOriginalAuthorUsername() { return originalAuthorUsername; }
        public String getOriginalAuthorDisplayName() { return originalAuthorDisplayName; }

        public boolean isReply() { return replyToId != null; }
        public boolean isRetweet() { return retweetOfId != null; }

        public int getLikes() { return likes; }
        public int getReplies() { return replies; }
        public int getRetweets() { return retweets; }

        public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
        public boolean isRetweetedByCurrentUser() { return retweetedByCurrentUser; }

        void setRetweetedByCurrentUser(boolean value) {
            this.retweetedByCurrentUser = value;
        }

        void incrementReplies() { replies++; }
        void decrementReplies() { replies = Math.max(0, replies - 1); }
        void incrementRetweets() { retweets++; }
        void decrementRetweets() { retweets = Math.max(0, retweets - 1); }

        public synchronized boolean toggleLike() {
            if (likedByCurrentUser) {
                likedByCurrentUser = false;
                likes = Math.max(0, likes - 1);
            }
            else {
                likedByCurrentUser = true;
                likes++;
            }
            return likedByCurrentUser;
        }
    }
}
