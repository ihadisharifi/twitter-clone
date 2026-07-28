package client;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import shared.models.User;

public final class UserAvatarHelper {

    private UserAvatarHelper() {}

    public static Node create(User user, double size) {
        StackPane avatar = new StackPane();
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);

        Circle background = new Circle(size / 2, Color.web("#262626"));
        background.setStroke(Color.web("#333333"));

        Label placeholder = new Label("👤");
        placeholder.setFont(Font.font(Math.max(14, size * .48)));
        placeholder.setTextFill(Color.web("#71767b"));
        avatar.getChildren().addAll(background, placeholder);

        String url = user == null ? null : user.getAvatarUrl();
        if (url != null && !url.isBlank()) {
            try {
                Image image = new Image(url, size * 2, size * 2, false, true);
                if (!image.isError()) {
                    ImageView view = new ImageView(image);
                    view.setFitWidth(size);
                    view.setFitHeight(size);
                    view.setPreserveRatio(false);
                    view.setClip(new Circle(size / 2, size / 2, size / 2));
                    avatar.getChildren().add(view);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return avatar;
    }
}
