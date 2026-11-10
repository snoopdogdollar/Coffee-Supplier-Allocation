package com.coffeemongers.controller;

import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.InMemoryAuthenticationService;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;

public class LoginController {
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private Button signInButton;

    @FXML private void login(){
        char[] password=passwordField.getText().toCharArray();
        try{DemoUser user=AppServices.authentication().login(emailField.getText(),password);messageLabel.setText("Welcome, "+user.fullName()+". Opening your workspace…");signInButton.setDisable(true);javafx.animation.PauseTransition transition=new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));transition.setOnFinished(event->WorkspaceRouter.open(emailField,user));transition.play();}
        catch(IllegalArgumentException ex){messageLabel.setText(ex.getMessage());passwordField.clear();}
        finally{java.util.Arrays.fill(password,'\0');if(signInButton.isDisabled())passwordField.clear();}
    }
    @FXML private void openRegistration(){SceneNavigator.show(emailField,"/com/coffeemongers/auth/Register.fxml","Coffee Mongers · Create account");}
    @FXML private void useDemoAdmin(){emailField.setText(InMemoryAuthenticationService.ADMIN_EMAIL);passwordField.setText(InMemoryAuthenticationService.ADMIN_PASSWORD);}
}
