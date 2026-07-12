package server.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import server.database.TempDataStore;
import shared.models.User;
import shared.protocol.Response;
import shared.protocol.StatusCode;

public class ProfileController {

    private final TempDataStore store = TempDataStore.get();
    private final Gson gson = new Gson();
    private final AuthController authController;

    public ProfileController(AuthController authController) {
        this.authController = authController;
    }

    public Response getProfile(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        User target = requester;
        if (body.has("username") && !body.get("username").isJsonNull()) {
            target = store.getUserByUsername(body.get("username").getAsString());
            if (target == null) {
                return Response.error(requestId, StatusCode.NOT_FOUND, "User not found.");
            }
        }
        return Response.ok(requestId, gson.toJsonTree(target));
    }
    
    public Response updateProfile(String requestId, JsonElement payload) {
        JsonObject body = payload.getAsJsonObject();
        User requester = authController.requireAuth(body);
        if (requester == null) {
            return Response.error(requestId, StatusCode.UNAUTHORIZED, "Invalid or expired session.");
        }

        if (body.has("displayName")) requester.setDisplayName(body.get("displayName").getAsString());
        if (body.has("bio")) requester.setBio(body.get("bio").getAsString());
        if (body.has("avatarUrl")) requester.setAvatarUrl(body.get("avatarUrl").getAsString());
        if (body.has("bannerUrl")) requester.setBannerUrl(body.get("bannerUrl").getAsString());

        return Response.ok(requestId, gson.toJsonTree(requester));
    }
}