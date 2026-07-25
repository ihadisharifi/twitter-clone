package server.database.dao;

import server.database.DatabaseConnection;

import java.sql.*;

public class LikeDao {

    public boolean likeTweet(int userId, int tweetId) throws SQLException {
        String sql = "INSERT INTO likes (user_id, tweet_id) VALUES (?, ?) " +
                "ON CONFLICT (user_id, tweet_id) DO NOTHING";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, tweetId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean unlikeTweet(int userId, int tweetId) throws SQLException {
        String sql = "DELETE FROM likes WHERE user_id = ? AND tweet_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, tweetId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean isLikedByUser(int userId, int tweetId) throws SQLException {
        String sql = "SELECT 1 FROM likes WHERE user_id = ? AND tweet_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, tweetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int getLikeCount(int tweetId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM likes WHERE tweet_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tweetId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("count");
            }
        }
    }
}