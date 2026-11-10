package com.coffeemongers.controller;

import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class RoleSwitcherController {
    @FXML private HBox roleSwitcher;
    @FXML private Button adminButton;
    @FXML private Button buyerButton;
    @FXML private Button supplierButton;
    @FXML private Button logoutButton;
    @FXML private Label accountLabel;

    @FXML public void initialize() {
        adminButton.setOnAction(e -> navigate("/com/coffeemongers/admin/AdminDashboard.fxml", "Admin Dashboard"));
        buyerButton.setOnAction(e -> navigate("/com/coffeemongers/view/BuyerDashboard.fxml", "Buyer Dashboard"));
        supplierButton.setOnAction(e -> navigate("/com/coffeemongers/supplier/SupplierDashboard.fxml", "Supplier Dashboard"));
        logoutButton.setOnAction(e->logout());
        AppServices.currentUser().ifPresentOrElse(this::showAccount,()->accountLabel.setText("Demo preview"));
    }

    private void showAccount(DemoUser user){
        accountLabel.setText(user.fullName()+" · "+user.email());
        boolean admin=user.role()==AccountRole.ADMIN;
        adminButton.setVisible(admin);adminButton.setManaged(admin);
        buyerButton.setVisible(admin||user.role()==AccountRole.BUYER);buyerButton.setManaged(buyerButton.isVisible());
        supplierButton.setVisible(admin||user.role()==AccountRole.SUPPLIER);supplierButton.setManaged(supplierButton.isVisible());
        AccountRole workspace=AppServices.session().activeWorkspace();
        markActive(adminButton,workspace==AccountRole.ADMIN);markActive(buyerButton,workspace==AccountRole.BUYER);markActive(supplierButton,workspace==AccountRole.SUPPLIER);
    }
    private void markActive(Button button,boolean active){button.getStyleClass().remove("workspace-active");if(active)button.getStyleClass().add("workspace-active");}

    private void navigate(String resource, String title) {
        Node source = roleSwitcher;
        if (source.getScene() == null || source.getScene().getWindow() == null) return;
        AccountRole selected=title.startsWith("Admin")?AccountRole.ADMIN:title.startsWith("Buyer")?AccountRole.BUYER:AccountRole.SUPPLIER;
        if(AppServices.currentUser().map(DemoUser::role).orElse(null)==AccountRole.ADMIN)AppServices.session().switchWorkspace(selected);
        SceneNavigator.clearAndShow(source, resource, "Coffee Mongers · " + title);
    }
    private void logout(){AppServices.session().end();SceneNavigator.clearAndShow(roleSwitcher,"/com/coffeemongers/auth/Login.fxml","Coffee Mongers · Sign in");}
}
