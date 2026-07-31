package client;

import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;


public final class UiIconHelper {

    public enum Icon {
        REPLY("M3 4 H21 V17 H9 L4 21 V17 H3 Z"),
        REPOST("M7 7 H19 L16 4 M19 7 L16 10 M17 17 H5 L8 14 M5 17 L8 20"),
        HEART("M12 21 C10 18 3 14 3 8 C3 3 9 2 12 6 C15 2 21 3 21 8 C21 14 14 18 12 21 Z"),
        BOOKMARK("M6 3 H18 V21 L12 17 L6 21 Z"),
        TRASH("M4 6 H20 M9 6 V4 H15 V6 M7 6 L8 21 H16 L17 6 M10 10 V17 M14 10 V17"),
        IMAGE("M3 4 H21 V20 H3 Z M6 16 L10 12 L13 15 L16 11 L21 17 M8 9 A1 1 0 1 0 8 7 A1 1 0 1 0 8 9"),
        USER("M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4 21 C4 16 8 14 12 14 C16 14 20 16 20 21");

        private final String path;

        Icon(String path) {
            this.path = path;
        }
    }

    private UiIconHelper() {}

    public static SVGPath create(Icon icon, String color, double scale) {
        SVGPath path = new SVGPath();
        path.setContent(icon.path);
        path.setFill(icon == Icon.HEART ? Color.web(color) : Color.TRANSPARENT);
        path.setStroke(Color.web(color));
        path.setStrokeWidth(1.8);
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    public static void apply(Button button, Icon icon, String color) {
        button.setGraphic(create(icon, color, 0.72));
        button.setGraphicTextGap(6);
        button.setMinWidth(28);
        button.setMinHeight(26);
    }
}
