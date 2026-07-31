package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.Session;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public class SessionDao {

    public Session createSession(int userId) throws SQLException {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(60L * 60 * 24);

        String sql = "INSERT INTO sessions (user_id, token, expires_at) VALUES (?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setTimestamp(3, Timestamp.from(expiresAt));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt("id");
                return new Session(id, userId, token, expiresAt.toString());
            }
        }
    }

    public Session getSession(String token) throws SQLException {
        if (token == null) return null;

        String sql = "SELECT * FROM sessions WHERE token = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
                if (expiresAt.isBefore(Instant.now())) {
                    deleteSession(token);
                    return null;
                }

                return new Session(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("token"),
                        expiresAt.toString()
                );
            }
        }
    }

    public void deleteSession(String token) throws SQLException {
        if (token == null) return;

        String sql = "DELETE FROM sessions WHERE token = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, token);
            ps.executeUpdate();
        }
    }
}