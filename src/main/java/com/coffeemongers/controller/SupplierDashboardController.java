package com.coffeemongers.controller;

import com.coffeemongers.service.*;
import com.coffeemongers.view.*;
import com.coffeeallocation.model.SupplierOverviewView;
import com.coffeeallocation.model.SupplierSupplyView;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.math.BigDecimal;
import java.util.List;

public class SupplierDashboardController {
    @FXML private BorderPane screenRoot;
    @FXML private SupplierSidebarController supplierSidebarController;
    @FXML private Label supplierHeading,productsCount,supplyCount,pendingOrdersCount,completedOrdersCount,supplierStatusLabel,weekLabel,ordersScopeLabel;
    @FXML private Button refreshDashboardButton;
    @FXML private DatePicker dashboardWeekPicker;
    @FXML private TableView<SupplierSupplyView.Row> overviewSupplyTable;
    @FXML private TableColumn<SupplierSupplyView.Row,String> productColumn,declaredColumn,allocatedColumn,remainingColumn,priceColumn;
    @FXML private TableView<SupplierOrderService.IncomingOrder> recentOrdersTable;
    @FXML private TableColumn<SupplierOrderService.IncomingOrder,String> orderColumn,buyerColumn,dateColumn,statusColumn;
    private SupplierOverviewService service;
    private boolean busy,queued,displayed;
    @FXML public void initialize() {
        supplierSidebarController.setActive("overview");
        productColumn.setCellValueFactory(c -> text(c.getValue().productName()));
        declaredColumn.setCellValueFactory(c -> text(amount(c.getValue().initialQuantity(),c.getValue().unit())));
        allocatedColumn.setCellValueFactory(c -> text(amount(c.getValue().initialQuantity().subtract(c.getValue().remainingQuantity()),c.getValue().unit())));
        remainingColumn.setCellValueFactory(c -> text(amount(c.getValue().remainingQuantity(),c.getValue().unit())));
        priceColumn.setCellValueFactory(c -> text(amount(c.getValue().unitPrice(),"VND/"+c.getValue().unit())));
        orderColumn.setCellValueFactory(c -> text("#"+c.getValue().order().id()));buyerColumn.setCellValueFactory(c -> text(c.getValue().shopName()));
        dateColumn.setCellValueFactory(c -> text(c.getValue().order().createdOn().toString()));statusColumn.setCellValueFactory(c -> text(c.getValue().order().status()));
        overviewSupplyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentOrdersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        service=AppServices.liveSupplierSupply().map(SupplierSupplyService::overview).orElse(null);
        SceneNavigator.onShown(screenRoot,() -> {if(displayed)refresh();else displayed=true;});
        refresh();
    }
    private static SimpleStringProperty text(String value){return new SimpleStringProperty(value);}
    private static String amount(BigDecimal value,String unit){return value.stripTrailingZeros().toPlainString()+" "+unit;}
    @FXML private void refresh() {
        if(busy){queued=true;return;}
        if(service==null){demo();return;}
        if(AppServices.liveSupplierSupply().isEmpty())return;
        LocalDate selected=dashboardWeekPicker.getValue();var user=AppServices.currentUser();
        busy=true;refreshDashboardButton.setDisable(true);dashboardWeekPicker.setDisable(true);
        for(var label:List.of(productsCount,supplyCount,pendingOrdersCount,completedOrdersCount))label.setText("—");
        overviewSupplyTable.getItems().clear();recentOrdersTable.getItems().clear();ordersScopeLabel.setText("");
        supplierStatusLabel.setText("Loading your products, weekly supply and incoming orders…");
        Task<SupplierOverviewView.Snapshot> task=new Task<>(){@Override protected SupplierOverviewView.Snapshot call()throws Exception{return service.load(selected);}};
        task.setOnSucceeded(event -> {
            finish();if(!AppServices.currentUser().equals(user)||AppServices.liveSupplierSupply().isEmpty())return;
            var snapshot=task.getValue();supplierHeading.setText(snapshot.catalog().supplierName());
            productsCount.setText(Integer.toString(snapshot.catalog().products().size()));supplyCount.setText(Integer.toString(snapshot.supply().size()));
            pendingOrdersCount.setText(Long.toString(snapshot.totals().pending()));completedOrdersCount.setText(Long.toString(snapshot.totals().completed()));
            showWeek(snapshot.week());overviewSupplyTable.getItems().setAll(snapshot.supply());
            recentOrdersTable.getItems().setAll(snapshot.orders().stream().map(o -> new SupplierOrderService.IncomingOrder(BuyerOrderService.toRecord(o),o.summary().shopId(),o.summary().shopName())).toList());
            overviewSupplyTable.setPlaceholder(new Label("No supply declared for this week. Open Weekly Supply to add a record."));
            recentOrdersTable.setPlaceholder(new Label("Orders appear after Admin confirms an allocation."));
            ordersScopeLabel.setText("All-time orders: "+snapshot.totals().orders()+" · showing up to 5 latest orders");
            supplierStatusLabel.setText("Supply is shown for the selected week. Order totals cover all dates. Buyer receipt updates order status.");
            refreshQueued();
        });
        task.setOnFailed(event -> {finish();if(AppServices.currentUser().equals(user)&&AppServices.liveSupplierSupply().isPresent())supplierStatusLabel.setText(DatabaseMessages.describe(task.getException(),"Could not load supplier activity. Refresh to try again."));refreshQueued();});
        Thread.ofVirtual().name("supplier-overview").start(task);
    }
    private void finish(){busy=false;refreshDashboardButton.setDisable(false);dashboardWeekPicker.setDisable(false);}
    private void refreshQueued(){if(queued){queued=false;if(screenRoot.getScene()!=null&&screenRoot.getScene().getWindow()!=null)refresh();}}
    private void showWeek(LocalDate monday){dashboardWeekPicker.setValue(monday);weekLabel.setText("%04d-W%02d".formatted(monday.get(IsoFields.WEEK_BASED_YEAR),monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));}
    private void demo() {
        var market=AppServices.marketplace();LocalDate week=SupplierOverviewService.monday(dashboardWeekPicker.getValue()==null?LocalDate.now():dashboardWeekPicker.getValue());showWeek(week);
        var supply=market.weeklySupply(week);var orders=market.supplierOrders();
        productsCount.setText(Integer.toString(market.supplierProducts().size()));supplyCount.setText(Integer.toString(supply.size()));
        pendingOrdersCount.setText(Long.toString(orders.stream().filter(o -> "PENDING".equalsIgnoreCase(o.status())||"Confirmed".equalsIgnoreCase(o.status())).count()));
        completedOrdersCount.setText(Long.toString(orders.stream().filter(o -> "COMPLETED".equalsIgnoreCase(o.status())||"Delivered".equalsIgnoreCase(o.status())).count()));
        overviewSupplyTable.getItems().setAll(supply.stream().map(r -> new SupplierSupplyView.Row(0,r.supplierProductId(),r.productName(),r.unit(),r.year(),r.week(),r.initialQuantity(),r.remainingQuantity(),r.unitPrice())).toList());
        recentOrdersTable.getItems().setAll(orders.stream().limit(5).map(o -> new SupplierOrderService.IncomingOrder(o,0,"Demo buyer")).toList());
        overviewSupplyTable.setPlaceholder(new Label("No demo supply for this week."));recentOrdersTable.setPlaceholder(new Label("Orders appear after Admin confirms an allocation in the connected workspace."));
        ordersScopeLabel.setText("Demo session orders: "+orders.size());supplierStatusLabel.setText("Demo session · products and supply remain in this session. The connected workspace shows saved data.");
    }
    @FXML private void openWeeklySupply(){SceneNavigator.replace(screenRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void openProducts(){SceneNavigator.replace(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products");}
    @FXML private void openOrders(){SceneNavigator.replace(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders");}
}
