package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.Tweet;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TweetDao {

    public Tweet createTweet(int authorId, String content, Integer replyToId, Integer retweetToId) throws SQLException {
        String sql = "INSERT INTO tweets (author_id, content, reply_to_id, retweet_to_id) " +
                "VALUES (?, ?, ?, ?) RETURNING id, created_at";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, authorId);
            ps.setString(2, content);
            setNullableInt(ps, 3, replyToId);
            setNullableInt(ps, 4, retweetToId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt("id");
                String createdAt = rs.getTimestamp("created_at").toInstant().toString();
                return new Tweet(id, authorId, content, replyToId, retweetToId, createdAt);
            }
        }
    }

    public Tweet getTweet(int id) throws SQLException {
        String sql = "SELECT * FROM tweets WHERE id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public boolean deleteTweet(int id, int requesterId) throws SQLException {
        String sql = "DELETE FROM tweets WHERE id = ? AND author_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.setInt(2, requesterId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Tweet> getTweetsByAuthor(int authorId) throws SQLException {
        String sql = "SELECT * FROM tweets WHERE author_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, authorId);
            return mapList(ps);
        }
    }

    public List<Tweet> getRepliesTo(int tweetId) throws SQLException {
        String sql = "SELECT * FROM tweets WHERE reply_to_id = ? ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tweetId);
            return mapList(ps);
        }
    }

    public List<Tweet> searchTweetsByKeyword(String query) throws SQLException {
        String sql = "SELECT * FROM tweets WHERE LOWER(content) LIKE ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + query.toLowerCase() + "%");
            return mapList(ps);
        }
    }

    public int getTweetCountByAuthor(int authorId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM tweets WHERE author_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("count");
            }
        }
    }

    private List<Tweet> mapList(PreparedStatement ps) throws SQLException {
        List<Tweet> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private Tweet mapRow(ResultSet rs) throws SQLException {
        return new Tweet(
                rs.getInt("id"),
                rs.getInt("author_id"),
                rs.getString("content"),
                getNullableInt(rs, "reply_to_id"),
                getNullableInt(rs, "retweet_to_id"),
                rs.getTimestamp("created_at").toInstant().toString()
        );
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}