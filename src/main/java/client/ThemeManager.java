package client;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import java.net.URL;

/**
 * Centralized theme manager responsible for toggling and applying CSS stylesheets
 * dynamically across application scenes and UI components.
 */
public final class ThemeManager {

    public enum Theme {
        DARK("/styles/dark-theme.css"),
        LIGHT("/styles/light-theme.css");

        private final String cssPath;

        Theme(String cssPath) {
            this.cssPath = cssPath;
        }

        public String getCssPath() {
            return cssPath;
        }
    }

    private ThemeManager() {}

    /**
     * Toggles between DARK and LIGHT themes in UserSession and updates the active scene stylesheet.
     */
    public static void toggleTheme(Scene scene) {
        Theme currentTheme = UserSession.getInstance().getTheme();
        Theme newTheme = (currentTheme == Theme.LIGHT) ? Theme.DARK : Theme.LIGHT;
        UserSession.getInstance().setTheme(newTheme);
        applyTheme(scene);
    }

    /**
     * Applies the current active theme stored in UserSession to the given scene.
     */
    public static void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        Theme activeTheme = UserSession.getInstance().getTheme();

        String darkCss = getResourceUrl(Theme.DARK.getCssPath());
        String lightCss = getResourceUrl(Theme.LIGHT.getCssPath());
        String legacyCss = getResourceUrl("/styles/twitter.css");

        // Remove old theme stylesheets to ensure dynamic replacement
        if (darkCss != null) scene.getStylesheets().remove(darkCss);
        if (lightCss != null) scene.getStylesheets().remove(lightCss);
        if (legacyCss != null) scene.getStylesheets().remove(legacyCss);

        if (scene.getRoot() != null) {
            if (darkCss != null) scene.getRoot().getStylesheets().remove(darkCss);
            if (lightCss != null) scene.getRoot().getStylesheets().remove(lightCss);
            if (legacyCss != null) scene.getRoot().getStylesheets().remove(legacyCss);
        }

        // Apply active theme stylesheet URL
        String targetCssUrl = getResourceUrl(activeTheme.getCssPath());
        if (targetCssUrl != null && !scene.getStylesheets().contains(targetCssUrl)) {
            scene.getStylesheets().add(targetCssUrl);
        }
    }

    /**
     * Helper to check if dark mode is currently active.
     */
    public static boolean isDarkMode() {
        return UserSession.getInstance().getTheme() == Theme.DARK;
    }

    /**
     * Dynamically updates a toggle button's text to show the option to switch modes.
     */
    public static void updateThemeButton(Button button) {
        if (button == null) {
            return;
        }
        if (isDarkMode()) {
            button.setText("☀️  Light Mode");
        } else {
            button.setText("🌙  Dark Mode");
        }
    }

    /**
     * Safely resolves a resource path to an external URL string.
     */
    public static String getResourceUrl(String resourcePath) {
        URL url = ThemeManager.class.getResource(resourcePath);
        return url != null ? url.toExternalForm() : null;
    }
}
