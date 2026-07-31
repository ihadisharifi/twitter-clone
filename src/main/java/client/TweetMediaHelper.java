package client;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;

import java.io.File;

public final class TweetMediaHelper {

    private TweetMediaHelper() {}

    public static boolean isVideoPath(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return false;
        }
        String lower = pathOrUri.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".m4v");
    }

    public static String toUriString(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            return null;
        }
        if (pathOrUri.startsWith("file:") || pathOrUri.startsWith("http:") || pathOrUri.startsWith("https:")) {
            return pathOrUri;
        }
        File file = new File(pathOrUri);
        return file.exists() ? file.toURI().toString() : null;
    }

    /**
     * Creates a JavaFX Node (ImageView for images/GIFs, or MediaView for videos)
     * constrained to maxWidth x maxHeight with rounded corners.
     */
    public static Node createMediaNode(String pathOrUri, double maxWidth, double maxHeight) {
        String uriString = toUriString(pathOrUri);
        if (uriString == null) {
            return null;
        }

        if (isVideoPath(uriString)) {
            try {
                Media media = new Media(uriString);
                MediaPlayer mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setAutoPlay(false);

                MediaView mediaView = new MediaView(mediaPlayer);
                mediaView.setFitWidth(maxWidth);
                mediaView.setFitHeight(maxHeight);
                mediaView.setPreserveRatio(true);

                Rectangle clip = new Rectangle();
                clip.setArcWidth(16);
                clip.setArcHeight(16);
                clip.widthProperty().bind(mediaView.layoutBoundsProperty().map(b -> b.getWidth() > 0 ? b.getWidth() : maxWidth));
                clip.heightProperty().bind(mediaView.layoutBoundsProperty().map(b -> b.getHeight() > 0 ? b.getHeight() : maxHeight));
                mediaView.setClip(clip);

                StackPane container = new StackPane();
                container.setAlignment(Pos.CENTER);

                Button playButton = new Button("▶");
                playButton.setStyle("-fx-background-color: rgba(15, 20, 25, 0.75); -fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-background-radius: 50%; -fx-min-width: 48px; -fx-min-height: 48px; -fx-cursor: hand;");

                Runnable togglePlay = () -> {
                    if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                        mediaPlayer.pause();
                        playButton.setText("▶");
                        playButton.setVisible(true);
                    } else {
                        mediaPlayer.play();
                        playButton.setText("⏸");
                    }
                };

                playButton.setOnAction(e -> togglePlay.run());
                mediaView.setOnMouseClicked(e -> togglePlay.run());

                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.stop();
                    playButton.setText("▶");
                    playButton.setVisible(true);
                });

                container.getChildren().addAll(mediaView, playButton);
                return container;

            } catch (Exception e) {
                System.err.println("Failed to load video media: " + uriString + " -> " + e.getMessage());
                return null;
            }
        } else {
            try {
                Image image = new Image(uriString, maxWidth, maxHeight, true, true);
                if (image.isError()) {
                    return null;
                }
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(maxWidth);
                imageView.setFitHeight(maxHeight);
                imageView.setPreserveRatio(true);

                Rectangle clip = new Rectangle();
                clip.setArcWidth(16);
                clip.setArcHeight(16);
                clip.widthProperty().bind(imageView.layoutBoundsProperty().map(b -> b.getWidth()));
                clip.heightProperty().bind(imageView.layoutBoundsProperty().map(b -> b.getHeight()));
                imageView.setClip(clip);

                return imageView;
            } catch (Exception e) {
                System.err.println("Failed to load image media: " + uriString + " -> " + e.getMessage());
                return null;
            }
        }
    }
}
