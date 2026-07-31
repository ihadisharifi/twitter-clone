package client;

import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public final class TweetCharacterLimit {

    public static final int MAX_CHARACTERS = 280;

    private TweetCharacterLimit() {}

    public static void enforce(TextArea input, Label counter) {
        counter.setText("0 / " + MAX_CHARACTERS);
        input.textProperty().addListener((observable, oldText, newText) -> {
            String value = newText == null ? "" : newText;
            int count = value.codePointCount(0, value.length());
            if (count > MAX_CHARACTERS) {
                int endIndex = value.offsetByCodePoints(0, MAX_CHARACTERS);
                input.setText(value.substring(0, endIndex));
                input.positionCaret(endIndex);
                return;
            }
            counter.setText(count + " / " + MAX_CHARACTERS);
        });
    }
}
