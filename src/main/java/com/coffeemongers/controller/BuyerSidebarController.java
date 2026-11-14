package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class BuyerSidebarController {
    @FXML private VBox buyerSidebarRoot;
    @FXML private Button overviewNavButton,demandsNavButton,ordersNavButton,createDemandNavButton;
    @FXML private Label buyerNameLabel,buyerEmailLabel;
    private String active="";
    @FXML public void initialize(){AppServices.currentUser().ifPresent(user -> {buyerNameLabel.setText(user.fullName());buyerEmailLabel.setText(user.email());});}
    public void setActive(String page) {
        active=page;
        Button[] buttons={overviewNavButton,demandsNavButton,ordersNavButton,createDemandNavButton};
        String[] pages={"overview","demands","orders","create"};
        for(int i=0;i<buttons.length;i++){buttons[i].getStyleClass().removeAll("nav-active","nav-button");buttons[i].getStyleClass().add(pages[i].equals(page)?"nav-active":"nav-button");}
    }
    @FXML private void overview(){if(!active.equals("overview"))WorkspaceRouter.openHome(buyerSidebarRoot);}
    @FXML private void demands(){if(!active.equals("demands"))SceneNavigator.replace(buyerSidebarRoot,"/com/coffeemongers/view/MyDemands.fxml","Coffee Mongers · My Demands");}
    @FXML private void orders(){if(!active.equals("orders"))SceneNavigator.replace(buyerSidebarRoot,"/com/coffeemongers/view/MyOrders.fxml","Coffee Mongers · My Orders");}
    @FXML private void create(){if(!active.equals("create"))SceneNavigator.show(buyerSidebarRoot,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
}
