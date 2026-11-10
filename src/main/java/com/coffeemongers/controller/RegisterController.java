package com.coffeemongers.controller;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class RegisterController {
    @FXML private TextField fullNameField,emailField;
    @FXML private PasswordField passwordField,confirmPasswordField;
    @FXML private ComboBox<String> rolePicker;
    @FXML private Label messageLabel;
    @FXML public void initialize(){rolePicker.setItems(FXCollections.observableArrayList("Buyer","Supplier"));rolePicker.setValue("Buyer");}
    @FXML private void register(){
        char[] password=passwordField.getText().toCharArray();char[] confirm=confirmPasswordField.getText().toCharArray();
        try{
            if(!java.util.Arrays.equals(password,confirm))throw new IllegalArgumentException("Passwords do not match.");
            AccountRole role="Supplier".equals(rolePicker.getValue())?AccountRole.SUPPLIER:AccountRole.BUYER;
            DemoUser user=AppServices.authentication().register(fullNameField.getText(),emailField.getText(),password,role);
            messageLabel.setText("Account created. Opening your "+role.name().toLowerCase()+" workspace…");
            javafx.animation.PauseTransition transition=new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));transition.setOnFinished(e->WorkspaceRouter.open(emailField,user));transition.play();
        }catch(IllegalArgumentException ex){messageLabel.setText(ex.getMessage());}
        finally{java.util.Arrays.fill(password,'\0');java.util.Arrays.fill(confirm,'\0');passwordField.clear();confirmPasswordField.clear();}
    }
    @FXML private void backToLogin(){SceneNavigator.back(emailField,"/com/coffeemongers/auth/Login.fxml","Coffee Mongers · Sign in");}
}
