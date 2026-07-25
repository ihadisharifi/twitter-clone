package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.Hashtag;
import shared.models.Tweet;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HashtagDao {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

    public List<Hashtag> extractAndStoreHashtags(int tweetId, String content) throws SQLException {
        List<Hashtag> tags = new ArrayList<>();
        if (content == null) return tags;

        try (Connection conn = DatabaseConnection.get()) {
            Matcher m = HASHTAG_PATTERN.matcher(content);
            while (m.find()) {
                String name = m.group(1).toLowerCase();
                Hashtag tag = findOrCreateHashtag(conn, name);
                if (tags.stream().noneMatch(t -> t.getId() == tag.getId())) {
                    tags.add(tag);
                }
                linkTweetToHashtag(conn, tweetId, tag.getId());
            }
        }
        return tags;
    }

    private Hashtag findOrCreateHashtag(Connection conn, String name) throws SQLException {
        String selectSql = "SELECT id, name FROM hashtags WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Hashtag(rs.getInt("id"), rs.getString("name"));
                }
            }
        }

        String insertSql = "INSERT INTO hashtags (name) VALUES (?) " +
                "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id, name";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Hashtag(rs.getInt("id"), rs.getString("name"));
            }
        }
    }

    private void linkTweetToHashtag(Connection conn, int tweetId, int hashtagId) throws SQLException {
        String sql = "INSERT INTO tweet_hashtags (tweet_id, hashtag_id) VALUES (?, ?) " +
                "ON CONFLICT (tweet_id, hashtag_id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tweetId);
            ps.setInt(2, hashtagId);
            ps.executeUpdate();
        }
    }

    public List<Hashtag> getHashtagsForTweet(int tweetId) throws SQLException {
        String sql = "SELECT h.id, h.name FROM hashtags h " +
                "JOIN tweet_hashtags th ON h.id = th.hashtag_id " +
                "WHERE th.tweet_id = ?";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tweetId);
            List<Hashtag> tags = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tags.add(new Hashtag(rs.getInt("id"), rs.getString("name")));
                }
            }
            return tags;
        }
    }

    public List<Tweet> getTweetsByHashtag(String tagName) throws SQLException {
        String sql = "SELECT t.* FROM tweets t " +
                "JOIN tweet_hashtags th ON t.id = th.tweet_id " +
                "JOIN hashtags h ON h.id = th.hashtag_id " +
                "WHERE LOWER(h.name) = LOWER(?) ORDER BY t.created_at DESC";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tagName);
            List<Tweet> tweets = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tweets.add(new Tweet(
                            rs.getInt("id"),
                            rs.getInt("author_id"),
                            rs.getString("content"),
                            getNullableInt(rs, "reply_to_id"),
                            getNullableInt(rs, "retweet_to_id"),
                            rs.getTimestamp("created_at").toInstant().toString()
                    ));
                }
            }
            return tweets;
        }
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}