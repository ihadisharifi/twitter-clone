package server.database.dao;

import server.database.DatabaseConnection;
import shared.models.Media;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MediaDao {

    public List<Media> attachMedia(int tweetId, List<String> urls) throws SQLException {
        List<Media> result = new ArrayList<>();
        if (urls == null || urls.isEmpty()) return result;

        String sql = "INSERT INTO media (tweet_id, url) VALUES (?, ?) RETURNING id";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (String url : urls) {
                ps.setInt(1, tweetId);
                ps.setString(2, url);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    result.add(new Media(rs.getInt("id"), tweetId, url));
                }
            }
        }
        return result;
    }

    public List<Media> getMedia(int tweetId) throws SQLException {
        String sql = "SELECT * FROM media WHERE tweet_id = ?";
        try (Connection conn = DatabaseConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tweetId);
            List<Media> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new Media(rs.getInt("id"), rs.getInt("tweet_id"), rs.getString("url")));
                }
            }
            return results;
        }
    }
}