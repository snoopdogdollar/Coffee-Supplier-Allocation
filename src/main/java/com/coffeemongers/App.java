
package com.coffeemongers;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        URL fxmlUrl = getClass().getResource("/com/coffeemongers/auth/Login.fxml");

        if (fxmlUrl == null) {
            throw new IllegalStateException("Login.fxml not found!");
        }

        System.out.println("Loading FXML: " + fxmlUrl);

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        System.out.println("Controller: " + loader.getController().getClass().getName());

        URL cssUrl = getClass().getResource(
                "/com/coffeemongers/view/buyer-dashboard.css"
        );

        Scene scene = new Scene(root, 1280, 800);

        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.out.println("CSS not found!");
        }

        stage.setTitle("Coffee Mongers · Sign in");
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(720);
        stage.setResizable(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
