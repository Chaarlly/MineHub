package com.br.minehub.main;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        Stage splashStage = new Stage();

        SplashScreen.show(splashStage, () -> Platform.runLater(() -> openMainWindow(primaryStage)));
    }

    private void openMainWindow(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("main-view.fxml"));

            Scene scene = new Scene(loader.load(), 1100, 650);
            scene.getStylesheets().add(Main.class.getResource("dark.css").toExternalForm());

            stage.initStyle(StageStyle.UNDECORATED);
            stage.getIcons().add(new Image(Main.class.getResourceAsStream("assets/logo.png")));
            stage.setTitle("MineHub");
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}