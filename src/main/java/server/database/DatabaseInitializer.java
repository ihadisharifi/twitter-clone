package server.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public final class DatabaseInitializer {

    private static final String SCHEMA_RESOURCE_PATH = "db/schema.sql";

    private DatabaseInitializer() {}

    public static void run() {
        String sql = readSchemaFile();
        String[] statements = sql.split(";");

        try (Connection conn = DatabaseConnection.get();
             Statement stmt = conn.createStatement()) {

            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) continue;
                stmt.execute(trimmed);
            }
            System.out.println("Database schema is up to date.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema.", e);
        }
    }

    private static String readSchemaFile() {
        try (InputStream in = DatabaseInitializer.class
                .getClassLoader()
                .getResourceAsStream(SCHEMA_RESOURCE_PATH)) {

            if (in == null) {
                throw new RuntimeException("Could not find " + SCHEMA_RESOURCE_PATH + " on classpath.");
            }

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("--")) continue;
                    builder.append(line).append("\n");
                }
            }
            return builder.toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + SCHEMA_RESOURCE_PATH, e);
        }
    }
}