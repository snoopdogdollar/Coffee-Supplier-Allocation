package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.SupplierSupplyService;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class SupplierDashboardController {
    @FXML private BorderPane screenRoot;
    @FXML private Label productsCount,supplyCount,ordersCount,supplierNameLabel;
    @FXML private Label supplierSubtitle, supplierStatusLabel;
    @FXML private Button productsNav, ordersNav, manageProductsButton;
    @FXML private VBox ordersStat;
    @FXML public void initialize() {
        AppServices.currentUser().ifPresent(user -> supplierNameLabel.setText(user.fullName()));
        var live = AppServices.liveSupplierSupply();
        if (live.isPresent()) {
            loadTestDashboard(live.get());
            return;
        }
        var service=AppServices.marketplace();
        productsCount.setText(String.valueOf(service.supplierProducts().size()));
        supplyCount.setText(String.valueOf(service.weeklySupply(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).size()));
        ordersCount.setText(String.valueOf(service.supplierOrders().size()));
    }

    private void loadTestDashboard(SupplierSupplyService service) {
        supplierSubtitle.setText("PostgreSQL test workspace · Manage your weekly supply.");
        for (var node : java.util.List.of(productsNav, ordersNav, manageProductsButton, ordersStat)) {
            node.setVisible(false);
            node.setManaged(false);
        }
        productsCount.setText("—");
        supplyCount.setText("—");
        supplierStatusLabel.setText("Loading supplier catalog and this week's supply…");
        Task<SupplierSupplyService.Snapshot> task = new Task<>() {
            @Override protected SupplierSupplyService.Snapshot call() throws Exception {
                return service.load(LocalDate.now());
            }
        };
        task.setOnSucceeded(event -> {
            var snapshot = task.getValue();
            supplierNameLabel.setText(snapshot.catalog().supplierName());
            productsCount.setText(Integer.toString(snapshot.catalog().products().size()));
            supplyCount.setText(Integer.toString(snapshot.rows().size()));
            supplierStatusLabel.setText("Select Weekly Supply to choose a delivery week.");
        });
        task.setOnFailed(event -> supplierStatusLabel.setText(task.getException() instanceof IllegalArgumentException
                ? task.getException().getMessage() : "Could not read the supplier test database. Check the connection settings."));
        Thread.ofVirtual().name("supplier-dashboard").start(task);
    }
    @FXML private void openDashboard(){WorkspaceRouter.openHome(screenRoot);}
    @FXML private void openWeeklySupply(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void openProducts(){if (AppServices.liveSupplierSupply().isEmpty()) SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products");}
    @FXML private void openOrders(){if (AppServices.liveSupplierSupply().isEmpty()) SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders");}
}
