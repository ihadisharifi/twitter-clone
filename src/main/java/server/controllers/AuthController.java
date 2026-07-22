package server.controllers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.dao.SessionDao;
import server.database.dao.UserDao;
import shared.models.Session;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

import java.sql.SQLException;

public class AuthController {

    private final UserDao userDao = new UserDao();
    private final SessionDao sessionDao = new SessionDao();
    private final Gson gson = new Gson();
    private static final int BCRYPT_COST = 12;

    public Response register(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        String username = getString(body, "username");
        String email = getString(body, "email");
        String displayName = getString(body, "displayName");
        String password = getString(body, "password");

        if (isBlank(username) || isBlank(email) || isBlank(displayName) || isBlank(password)) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "All fields are required.");
        }

        if (password.length() < 8) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "Password must be at least 8 characters.");
        }

        try {
            if (userDao.usernameTaken(username) || userDao.emailTaken(email)) {
                return Response.error(requestId, StatusCode.CONFLICT, "Username or email already exists.");
            }

            User user = userDao.createUser(username, email, displayName, hash(password));
            Session session = sessionDao.createSession(user.getId());

            JsonObject result = new JsonObject();
            result.add("user", gson.toJsonTree(user));
            result.addProperty("token", session.getToken());
            return Response.ok(requestId, result);

        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    public Response login(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        String username = getString(body, "username");
        String password = getString(body, "password");

        if (isBlank(username) || isBlank(password)) {
            return Response.error(requestId, StatusCode.BAD_REQUEST, "Username and password are required.");
        }

        try {
            User user = userDao.getUserByUsername(username);
            if (user == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "Account not found.");
            }
            if (!verify(password, userDao.getPasswordHash(user.getId()))) {
                return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid username or password.");
            }

            Session session = sessionDao.createSession(user.getId());
            JsonObject result = new JsonObject();
            result.add("user", gson.toJsonTree(user));
            result.addProperty("token", session.getToken());
            return Response.ok(requestId, result);

        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    public Response logout(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        try {
            sessionDao.deleteSession(getString(body, "token"));
            return Response.ok(requestId, null);
        } catch (SQLException e) {
            return Response.error(requestId, StatusCode.SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    /**
     * Used by every other controller to resolve the calling user from a session token.
     * Returns null on any failure (missing/expired session, or a DB error) — callers
     * already treat a null user as "unauthorized".
     */
    public User requireAuth(JsonObject body) {
        try {
            Session session = sessionDao.getSession(getString(body, "token"));
            if (session == null) return null;
            return userDao.getUserById(session.getUserId());
        } catch (SQLException e) {
            System.err.println("Database error during auth check: " + e.getMessage());
            return null;
        }
    }

    private static String hash(String raw) {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, raw.toCharArray());
    }

    private static boolean verify(String raw, String hashed) {
        if (hashed == null) return false;
        return BCrypt.verifyer().verify(raw.toCharArray(), hashed).verified;
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}