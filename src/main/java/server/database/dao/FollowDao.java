package server.database.dao;

import server.database.DatabaseConnection;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class FollowDao {

    public boolean follow(int followerId, int followingId) throws SQLException {
        if (followerId == followingId) return false;

        String sql = "INSERT INTO follows (follower_id, following_id) VALUES (?, ?) " +
                "ON CONFLICT (follower_id, following_id) DO NOTHING";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, followerId);
            ps.setInt(2, followingId);
            ps.executeUpdate();
            return true;
        }
    }

    public boolean unfollow(int followerId, int followingId) throws SQLException {
        String sql = "DELETE FROM follows WHERE follower_id = ? AND following_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, followerId);
            ps.setInt(2, followingId);
            ps.executeUpdate();
            return true;
        }
    }

    public boolean isFollowing(int followerId, int followingId) throws SQLException {
        String sql = "SELECT 1 FROM follows WHERE follower_id = ? AND following_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, followerId);
            ps.setInt(2, followingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Set<Integer> getFollowerIds(int userId) throws SQLException {
        String sql = "SELECT follower_id FROM follows WHERE following_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            Set<Integer> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("follower_id"));
                }
            }
            return ids;
        }
    }

    public Set<Integer> getFollowingIds(int userId) throws SQLException {
        String sql = "SELECT following_id FROM follows WHERE follower_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            Set<Integer> ids = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("following_id"));
                }
            }
            return ids;
        }
    }
}