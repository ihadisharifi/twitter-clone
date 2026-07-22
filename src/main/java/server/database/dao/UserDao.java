package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    public User createUser(String username, String email, String displayName, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, display_name, bio, avatar_url, banner_url) " +
                "VALUES (?, ?, ?, ?, '', NULL, NULL) RETURNING id, created_at";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, displayName);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt("id");
                String createdAt = rs.getTimestamp("created_at").toInstant().toString();
                return new User(id, username, email, displayName, "", null, null, createdAt);
            }
        }
    }

    public User getUserById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public boolean usernameTaken(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean emailTaken(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String getPasswordHash(int userId) throws SQLException {
        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("password_hash") : null;
            }
        }
    }

    public List<User> searchUsersByQuery(String query) throws SQLException {
        String sql = "SELECT * FROM users WHERE LOWER(username) LIKE ? OR LOWER(display_name) LIKE ?";
        String likeTerm = "%" + query.toLowerCase() + "%";

        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, likeTerm);
            ps.setString(2, likeTerm);

            List<User> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
        }
    }

    public User updateProfile(int userId, String displayName, String bio, String avatarUrl, String bannerUrl) throws SQLException {
        String sql = "UPDATE users SET display_name = ?, bio = ?, avatar_url = ?, banner_url = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, displayName);
            ps.setString(2, bio);
            ps.setString(3, avatarUrl);
            ps.setString(4, bannerUrl);
            ps.setInt(5, userId);
            ps.executeUpdate();
        }
        return getUserById(userId);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("bio"),
                rs.getString("avatar_url"),
                rs.getString("banner_url"),
                rs.getTimestamp("created_at").toInstant().toString()
        );
    }
}