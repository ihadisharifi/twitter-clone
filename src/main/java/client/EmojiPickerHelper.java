package client;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.stage.Popup;
import javafx.stage.Window;

public final class EmojiPickerHelper {

    private EmojiPickerHelper() {}

    private static final String[] EMOJIS = {
            "😀", "😂", "😍", "🔥", "❤️", "👍", "🎉", "🚀",
            "✨", "💯", "🤔", "🙌", "😭", "🙏", "😎", "🥳",
            "👀", "💡", "💪", "✌️", "💬", "⚡", "🌟", "🤣",
            "😴", "🙈", "⚽", "🎵", "☕", "🍿", "📌", "🎈"
    };

    public static void showEmojiPicker(Button anchorButton, TextArea targetTextArea) {
        if (anchorButton == null || targetTextArea == null) {
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);

        FlowPane emojiGrid = new FlowPane();
        emojiGrid.setHgap(4);
        emojiGrid.setVgap(4);
        emojiGrid.setPrefWrapLength(240);
        emojiGrid.setPadding(new Insets(10));
        emojiGrid.setStyle(
                "-fx-background-color: #16181c; " +
                "-fx-border-color: #333333; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.6), 12, 0, 0, 4);"
        );

        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 18px; -fx-padding: 4 6; -fx-cursor: hand;");
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #202429; -fx-background-radius: 8; -fx-font-size: 18px; -fx-padding: 4 6; -fx-cursor: hand;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-padding: 4 6; -fx-cursor: hand;"));
            btn.setOnAction(e -> {
                insertEmoji(targetTextArea, emoji);
                popup.hide();
            });
            emojiGrid.getChildren().add(btn);
        }

        popup.getContent().add(emojiGrid);

        Window window = anchorButton.getScene() != null ? anchorButton.getScene().getWindow() : null;
        if (window != null) {
            Bounds bounds = anchorButton.localToScreen(anchorButton.getBoundsInLocal());
            if (bounds != null) {
                popup.show(anchorButton, bounds.getMinX(), bounds.getMinY() - 195);
            } else {
                popup.show(window);
            }
        }
    }

    public static void insertEmoji(TextArea textArea, String emoji) {
        if (textArea == null || emoji == null) {
            return;
        }
        int caretPos = textArea.getCaretPosition();
        String text = textArea.getText() != null ? textArea.getText() : "";
        if (caretPos >= 0 && caretPos <= text.length()) {
            textArea.setText(text.substring(0, caretPos) + emoji + text.substring(caretPos));
            textArea.positionCaret(caretPos + emoji.length());
        } else {
            textArea.appendText(emoji);
        }
        textArea.requestFocus();
    }
}
