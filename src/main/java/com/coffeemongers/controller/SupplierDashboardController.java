package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class SupplierDashboardController {
    @FXML private BorderPane screenRoot;
    @FXML private Label productsCount,supplyCount,ordersCount,supplierNameLabel;
    @FXML public void initialize(){AppServices.currentUser().ifPresent(user->supplierNameLabel.setText(user.fullName()));var service=AppServices.marketplace();productsCount.setText(String.valueOf(service.supplierProducts().size()));supplyCount.setText(String.valueOf(service.weeklySupply(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).size()));ordersCount.setText(String.valueOf(service.supplierOrders().size()));}
    @FXML private void openDashboard(){WorkspaceRouter.openHome(screenRoot);}
    @FXML private void openWeeklySupply(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void openProducts(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products");}
    @FXML private void openOrders(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders");}
}
