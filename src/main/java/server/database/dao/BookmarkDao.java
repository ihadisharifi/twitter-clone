package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.Tweet;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookmarkDao {
    public void bookmark(int userId, int tweetId) throws SQLException {
        try (Connection conn = DatabaseConnection.get()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO bookmarks (user_id, tweet_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                ps.setInt(1, userId); ps.setInt(2, tweetId); ps.executeUpdate();
            }
        }
    }

    public void unbookmark(int userId, int tweetId) throws SQLException {
        try (Connection conn = DatabaseConnection.get()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM bookmarks WHERE user_id = ? AND tweet_id = ?")) {
                ps.setInt(1, userId); ps.setInt(2, tweetId); ps.executeUpdate();
            }
        }
    }

    public List<Tweet> getBookmarks(int userId) throws SQLException {
        String sql = "SELECT t.* FROM bookmarks b JOIN tweets t ON t.id = b.tweet_id "
                + "WHERE b.user_id = ? ORDER BY b.created_at DESC";
        List<Tweet> tweets = new ArrayList<>();
        try (Connection conn = DatabaseConnection.get()) {
            ensureTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) tweets.add(new Tweet(rs.getInt("id"), rs.getInt("author_id"),
                            rs.getString("content"), nullableInt(rs, "reply_to_id"),
                            nullableInt(rs, "retweet_to_id"), rs.getTimestamp("created_at").toInstant().toString()));
                }
            }
        }
        return tweets;
    }

    private void ensureTable(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bookmarks ("
                    + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                    + "tweet_id INTEGER NOT NULL REFERENCES tweets(id) ON DELETE CASCADE, "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (user_id, tweet_id))");
        }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
