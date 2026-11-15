package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class SupplierSidebarController {
    @FXML private VBox supplierSidebarRoot;
    @FXML private Label supplierNameLabel,supplierEmailLabel;
    @FXML private Button dashboardNav,productsNav,weeklySupplyNav,ordersNav;
    private String active="";
    @FXML public void initialize(){AppServices.currentUser().ifPresent(user -> {supplierNameLabel.setText(user.fullName());supplierEmailLabel.setText(user.email());});}
    public void setActive(String page){active=page;var buttons=new Button[]{dashboardNav,productsNav,weeklySupplyNav,ordersNav};var pages=new String[]{"overview","products","supply","orders"};for(int i=0;i<buttons.length;i++){buttons[i].getStyleClass().removeAll("nav-active","nav-button");buttons[i].getStyleClass().add(page.equals(pages[i])?"nav-active":"nav-button");}}
    @FXML private void dashboard(){if(!active.equals("overview"))WorkspaceRouter.openHome(supplierSidebarRoot);}
    @FXML private void products(){if(!active.equals("products"))SceneNavigator.replace(supplierSidebarRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products");}
    @FXML private void supply(){if(!active.equals("supply"))SceneNavigator.replace(supplierSidebarRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void orders(){if(!active.equals("orders"))SceneNavigator.replace(supplierSidebarRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders");}
}
