package com.br.minehub.main;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Random;

public class SplashScreen {

    public static void show(Stage splashStage, Runnable onFinish) {
        Image logoImage = new Image(Main.class.getResourceAsStream("assets/logo.png"));

        ImageView logo = new ImageView(logoImage);
        logo.setFitWidth(145);
        logo.setFitHeight(145);
        logo.setPreserveRatio(true);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#ef4444"));
        glow.setRadius(38);
        glow.setSpread(0.35);
        logo.setEffect(glow);

        Label title = new Label("MINEHUB");
        title.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 34px;
                -fx-font-weight: 900;
                """);

        Label subtitle = new Label("GERENCIE • CONECTE • DOMINE");
        subtitle.setStyle("""
                -fx-text-fill: #ef4444;
                -fx-font-size: 11px;
                -fx-font-weight: 800;
                -fx-letter-spacing: 4px;
                """);

        Label status = new Label("Inicializando MineHub...");
        status.setStyle("""
                -fx-text-fill: #9ca3af;
                -fx-font-size: 13px;
                """);

        Label percent = new Label("0%");
        percent.setStyle("""
                -fx-text-fill: #ef4444;
                -fx-font-size: 13px;
                -fx-font-weight: 800;
                """);

        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(360);
        progress.setStyle("""
                -fx-accent: #ef4444;
                -fx-control-inner-background: #111827;
                """);

        VBox content = new VBox(12, logo, title, subtitle, progress, status, percent);
        content.setAlignment(Pos.CENTER);

        Pane particles = createParticles();

        StackPane root = new StackPane(particles, content);
        root.setStyle("""
                -fx-background-color:
                    radial-gradient(center 50% 42%, radius 70%, #1f2937, #090d14 65%, #020617);
                -fx-border-color: #1f2937;
                -fx-border-width: 1;
                """);

        Scene scene = new Scene(root, 560, 430);
        scene.setFill(Color.TRANSPARENT);

        splashStage.initStyle(StageStyle.UNDECORATED);
        splashStage.getIcons().add(logoImage);
        splashStage.setScene(scene);
        splashStage.centerOnScreen();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(450), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.25), logo);
        pulse.setFromX(1);
        pulse.setFromY(1);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        Timeline glowPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 25),
                        new KeyValue(glow.spreadProperty(), 0.20)
                ),
                new KeyFrame(Duration.seconds(1.2),
                        new KeyValue(glow.radiusProperty(), 55),
                        new KeyValue(glow.spreadProperty(), 0.45)
                )
        );
        glowPulse.setAutoReverse(true);
        glowPulse.setCycleCount(Animation.INDEFINITE);

        Timeline loading = new Timeline(
                step(progress, percent, status, 0.10, "Carregando interface...", 0.25),
                step(progress, percent, status, 0.25, "Inicializando SFTP...", 0.65),
                step(progress, percent, status, 0.45, "Preparando Pterodactyl...", 1.05),
                step(progress, percent, status, 0.65, "Carregando editor YAML...", 1.45),
                step(progress, percent, status, 0.82, "Preparando terminal...", 1.85),
                step(progress, percent, status, 1.00, "Pronto.", 2.35)
        );

        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), root);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        loading.setOnFinished(e -> {
            pulse.stop();
            glowPulse.stop();

            fadeOut.setOnFinished(ev -> {
                splashStage.close();
                onFinish.run();
            });

            fadeOut.play();
        });

        splashStage.show();

        fadeIn.play();
        pulse.play();
        glowPulse.play();
        loading.play();
    }

    private static KeyFrame step(
            ProgressBar progress,
            Label percent,
            Label status,
            double value,
            String text,
            double seconds
    ) {
        return new KeyFrame(Duration.seconds(seconds), e -> {
            status.setText(text);
            percent.setText((int) (value * 100) + "%");
        }, new KeyValue(progress.progressProperty(), value));
    }

    private static Pane createParticles() {
        Pane pane = new Pane();
        pane.setPrefSize(560, 430);

        Random random = new Random();

        for (int i = 0; i < 34; i++) {
            Circle particle = new Circle(random.nextDouble() * 2.2 + 1);
            particle.setFill(Color.web("#ef4444", random.nextDouble() * 0.65 + 0.25));
            particle.setTranslateX(random.nextDouble() * 560);
            particle.setTranslateY(random.nextDouble() * 430);

            TranslateTransition move = new TranslateTransition(
                    Duration.seconds(random.nextDouble() * 3 + 3),
                    particle
            );

            move.setByY(-(random.nextDouble() * 80 + 40));
            move.setByX(random.nextDouble() * 40 - 20);
            move.setAutoReverse(true);
            move.setCycleCount(Animation.INDEFINITE);

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(random.nextDouble() * 2 + 1.5),
                    particle
            );

            fade.setFromValue(0.15);
            fade.setToValue(0.9);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);

            move.play();
            fade.play();

            pane.getChildren().add(particle);
        }

        return pane;
    }
}