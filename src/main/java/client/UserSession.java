package client;
import shared.models.User;
import shared.models.Session;

public class UserSession {
    private static UserSession instance;

    private User currentUser;
    private Session currentSession;

    private String avatarImagePath;
    private String bannerImagePath;
    private ThemeManager.Theme activeTheme = ThemeManager.Theme.DARK;

    private UserSession() {}

    /**
     * Globally retrieves the active session context instance channel.
     */
    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    /**
     * Initializes user variables upon successful login or registration.
     */
    public void startSession(User user, Session session) {
        this.currentUser = user;
        this.currentSession = session;
        if (user != null && session != null) {
            System.out.println("Session Activated: Welcome @" + user.getUsername() + " (" + user.getDisplayName() + ")");
            System.out.println("Token Secured in UI Context: [" + session.getToken() + "]");
        }
    }

    /**
     * Purges all reference pointers upon logout to secure application state.
     */
    public void clearSession() {
        if (this.currentUser != null) {
            System.out.println("Session Terminated: @" + this.currentUser.getUsername() + " logged out safely.");
        }
        this.currentUser = null;
        this.currentSession = null;
        this.avatarImagePath = null;
        this.bannerImagePath = null;
    }

    public String getAvatarImagePath() {
        if (avatarImagePath != null) {
            return avatarImagePath;
        }
        return currentUser != null ? currentUser.getAvatarUrl() : null;
    }

    public void setAvatarImagePath(String path) {
        this.avatarImagePath = path;
        if (currentUser != null) {
            currentUser.setAvatarUrl(path);
        }
    }

    public String getBannerImagePath() {
        if (bannerImagePath != null) {
            return bannerImagePath;
        }
        return currentUser != null ? currentUser.getBannerUrl() : null;
    }

    public void setBannerImagePath(String path) {
        this.bannerImagePath = path;
        if (currentUser != null) {
            currentUser.setBannerUrl(path);
        }
    }

    // Helper utilities to cleanly request fields across FX controllers
    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }

    public String getDisplayName() {
        return currentUser != null ? currentUser.getDisplayName() : null;
    }

    public String getToken() {
        return currentSession != null ? currentSession.getToken() : null;
    }

    public User getCurrentUser() { return currentUser; }
    public Session getCurrentSession() { return currentSession; }

    public ThemeManager.Theme getTheme() {
        return activeTheme != null ? activeTheme : ThemeManager.Theme.DARK;
    }

    public void setTheme(ThemeManager.Theme theme) {
        if (theme != null) {
            this.activeTheme = theme;
        }
    }

    public boolean isDarkMode() {
        return getTheme() == ThemeManager.Theme.DARK;
    }
}
