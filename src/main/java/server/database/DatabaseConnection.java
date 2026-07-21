package server.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;


public final class DatabaseConnection {

    private static final Properties PROPS = loadProperties();

    private DatabaseConnection() {}

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties not found on classpath.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load db.properties", e);
        }
        return props;
    }


    public static Connection get() throws SQLException {
        String url = PROPS.getProperty("db.url");
        String user = PROPS.getProperty("db.user");
        String password = PROPS.getProperty("db.password");
        return DriverManager.getConnection(url, user, password);
    }
}