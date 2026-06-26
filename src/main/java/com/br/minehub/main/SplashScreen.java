package com.br.minehub.main;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class SplashScreen {

    public static void show(Stage splashStage, Runnable onFinish) {
        ImageView logo = new ImageView(
                new Image(Main.class.getResourceAsStream("assets/logo.png"))
        );

        logo.setFitWidth(150);
        logo.setFitHeight(150);
        logo.setPreserveRatio(true);
        logo.setEffect(new DropShadow(35, Color.web("#ef4444")));

        Label title = new Label("MINEHUB");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: 900; -fx-letter-spacing: 8px;");

        Label status = new Label("Iniciando MineHub...");
        status.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px;");

        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(360);
        progress.setStyle("""
                -fx-accent: #ef4444;
                -fx-control-inner-background: #111827;
                """);

        VBox root = new VBox(18, logo, title, progress, status);
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
                -fx-background-color:
                    radial-gradient(center 50% 40%, radius 70%, #1f2937, #090d14 65%, #020617);
                -fx-border-color: #1f2937;
                -fx-border-width: 1;
                """);

        Scene scene = new Scene(root, 520, 420);
        scene.setFill(Color.TRANSPARENT);

        splashStage.initStyle(StageStyle.UNDECORATED);
        splashStage.getIcons().add(new Image(Main.class.getResourceAsStream("assets/logo.png")));
        splashStage.setScene(scene);
        splashStage.centerOnScreen();

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progress.progressProperty(), 0)
                ),
                new KeyFrame(Duration.seconds(0.8), e -> status.setText("Carregando interface..."),
                        new KeyValue(progress.progressProperty(), 0.35)
                ),
                new KeyFrame(Duration.seconds(1.6), e -> status.setText("Preparando serviços..."),
                        new KeyValue(progress.progressProperty(), 0.70)
                ),
                new KeyFrame(Duration.seconds(2.4), e -> status.setText("Pronto."),
                        new KeyValue(progress.progressProperty(), 1)
                )
        );

        FadeTransition fadeIn = new FadeTransition(Duration.millis(450), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(450), root);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        timeline.setOnFinished(e -> {
            fadeOut.setOnFinished(ev -> {
                splashStage.close();
                onFinish.run();
            });
            fadeOut.play();
        });

        splashStage.show();
        fadeIn.play();
        timeline.play();
    }
}