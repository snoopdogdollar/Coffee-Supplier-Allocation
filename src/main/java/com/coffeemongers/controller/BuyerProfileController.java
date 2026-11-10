package com.coffeemongers.controller;

import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public final class BuyerProfileController {
    @FXML private BorderPane screenRoot;
    @FXML private Label nameLabel,emailLabel,roleLabel,userIdLabel,profileMessage;
    @FXML public void initialize(){AppServices.currentUser().ifPresentOrElse(this::showUser,()->profileMessage.setText("Sign in as a Buyer to view account details."));}
    private void showUser(DemoUser user){nameLabel.setText(user.fullName());emailLabel.setText(user.email());roleLabel.setText(user.role().name());userIdLabel.setText(user.userId());profileMessage.setText("Demo account details are held in memory and cleared when the application closes.");}
    @FXML private void goBack(){SceneNavigator.back(screenRoot,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");}
}
