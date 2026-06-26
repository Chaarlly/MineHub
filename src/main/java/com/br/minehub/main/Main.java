package com.br.minehub.main;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        Stage splashStage = new Stage();

        SplashScreen.show(splashStage, () -> Platform.runLater(() -> openMainWindow(primaryStage)));
    }

    private void openMainWindow(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("main-view.fxml"));

            Parent root = loader.load();
            root.setStyle("-fx-background-color: #0b0f19;");

            Scene scene = new Scene(root, 1100, 650);
            scene.setFill(Color.web("#0b0f19"));
            scene.getStylesheets().add(Main.class.getResource("dark.css").toExternalForm());

            stage.initStyle(StageStyle.UNDECORATED);
            stage.getIcons().add(new Image(Main.class.getResourceAsStream("assets/logo.png")));
            stage.setTitle("MineHub");
            stage.setScene(scene);
            stage.centerOnScreen();

            stage.setOpacity(0);
            stage.show();

            FadeTransition fade = new FadeTransition(Duration.millis(350), root);
            fade.setFromValue(0);
            fade.setToValue(1);

            Timeline opacity = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(350), new KeyValue(stage.opacityProperty(), 1))
            );

            fade.play();
            opacity.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}