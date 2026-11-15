package com.coffeemongers.controller;

import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerWorkspaceService;
import com.coffeemongers.view.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.util.List;

public class BuyerDashboardController {
    @FXML private BorderPane buyerRoot;
    @FXML private BuyerSidebarController buyerSidebarController;
    @FXML private Label shopHeading,dashboardSubtitle,totalDemandsLabel,pendingDemandsLabel,pendingOrdersLabel,reviewsDueLabel,workspaceStatus;
    @FXML private TableView<BuyerDemandRow> recentDemandsTable;
    @FXML private TableColumn<BuyerDemandRow,String> demandIdColumn,productColumn,quantityColumn,weekColumn,statusColumn;
    @FXML private TableView<OrderRecord> recentOrdersTable;
    @FXML private TableColumn<OrderRecord,String> orderIdColumn,orderSupplierColumn,orderDemandColumn,orderStatusColumn;
    @FXML private Button refreshDemandsButton;
    private BuyerWorkspaceService service;
    private boolean busy,queued,displayed;
    @FXML public void initialize() {
        buyerSidebarController.setActive("overview");
        demandIdColumn.setCellValueFactory(c -> text(c.getValue().id()));productColumn.setCellValueFactory(c -> text(c.getValue().products()));
        quantityColumn.setCellValueFactory(c -> text(c.getValue().quantities()));weekColumn.setCellValueFactory(c -> text(c.getValue().week()));
        statusColumn.setCellValueFactory(c -> text(c.getValue().status()));
        statusColumn.setCellFactory(column -> DemandStatusPresentation.cell());
        orderIdColumn.setCellValueFactory(c -> text("#"+c.getValue().id()));orderDemandColumn.setCellValueFactory(c -> text(c.getValue().demandId()));
        orderSupplierColumn.setCellValueFactory(c -> text(c.getValue().supplierName()));orderStatusColumn.setCellValueFactory(c -> text(c.getValue().status()));
        recentDemandsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentOrdersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentDemandsTable.setRowFactory(table -> {var row=new TableRow<BuyerDemandRow>();row.setOnMouseClicked(event -> {if(event.getClickCount()==2&&!row.isEmpty())row.getItem().open(buyerRoot);});return row;});
        service=AppServices.liveBuyerDemands().map(s -> s.workspace()).orElse(null);
        buyerRoot.sceneProperty().addListener((o,old,scene) -> {if(scene!=null)scene.windowProperty().addListener((p,previous,window) -> {if(window!=null){if(displayed)refresh();else displayed=true;}});});
        refresh();
    }
    private static SimpleStringProperty text(String value){return new SimpleStringProperty(value);}
    @FXML private void refresh() {
        if(busy){queued=true;return;}
        if(service==null){demo();return;}
        if(AppServices.liveBuyerDemands().isEmpty())return;
        var user=AppServices.currentUser();busy=true;refreshDemandsButton.setDisable(true);
        recentDemandsTable.getItems().clear();recentOrdersTable.getItems().clear();
        for(var label:List.of(totalDemandsLabel,pendingDemandsLabel,pendingOrdersLabel,reviewsDueLabel))label.setText("—");
        workspaceStatus.setText("Loading your shop's activity…");
        Task<BuyerWorkspaceService.Dashboard> task=new Task<>(){@Override protected BuyerWorkspaceService.Dashboard call()throws Exception{return service.dashboard();}};
        task.setOnSucceeded(e -> {
            finish();if(!AppServices.currentUser().equals(user)||AppServices.liveBuyerDemands().isEmpty())return;
            var snapshot=task.getValue();var counts=snapshot.counts();shopHeading.setText(snapshot.shop().name());
            totalDemandsLabel.setText(Long.toString(counts.demands()));pendingDemandsLabel.setText(Long.toString(counts.openDemands()));
            pendingOrdersLabel.setText(Long.toString(counts.pendingOrders()));reviewsDueLabel.setText(Long.toString(counts.reviewsDue()));
            recentDemandsTable.getItems().setAll(snapshot.demands().stream().map(BuyerDemandRow::of).toList());
            recentOrdersTable.getItems().setAll(snapshot.orders());
            recentDemandsTable.setPlaceholder(new Label("No demands yet. Create your first request."));
            recentOrdersTable.setPlaceholder(new Label("Orders appear after Admin confirms an allocation."));
            dashboardSubtitle.setText("Your demands, allocated orders and next steps — all in one place.");
            workspaceStatus.setText("All-time totals for your shop · showing up to 5 latest demands and orders.");refreshQueued();
        });
        task.setOnFailed(e -> {finish();if(AppServices.currentUser().equals(user))workspaceStatus.setText(DatabaseMessages.describe(task.getException(),"Could not load your activity. Refresh to try again."));refreshQueued();});
        Thread.ofVirtual().name("buyer-overview").start(task);
    }
    private void finish(){busy=false;refreshDemandsButton.setDisable(false);}
    private void refreshQueued(){if(queued){queued=false;if(buyerRoot.getScene()!=null&&buyerRoot.getScene().getWindow()!=null)refresh();}}
    private void demo() {
        var market=AppServices.marketplace();var demands=market.recentDemands();var orders=market.buyerOrders();
        totalDemandsLabel.setText(Integer.toString(demands.size()));pendingDemandsLabel.setText(Long.toString(demands.stream().filter(d -> orders.stream().noneMatch(o -> o.demandId().equals(d.id()))).count()));
        pendingOrdersLabel.setText(Long.toString(orders.stream().filter(o -> "PENDING".equalsIgnoreCase(o.status())||"Confirmed".equalsIgnoreCase(o.status())).count()));
        reviewsDueLabel.setText(Long.toString(orders.stream().filter(o -> !o.reviewed()&&("COMPLETED".equalsIgnoreCase(o.status())||"Delivered".equalsIgnoreCase(o.status()))).count()));
        recentDemandsTable.getItems().setAll(demands.stream().limit(5).map(BuyerDemandRow::demo).toList());recentOrdersTable.getItems().setAll(orders.stream().limit(5).toList());
        recentDemandsTable.setPlaceholder(new Label("Create a demand to get started."));recentOrdersTable.setPlaceholder(new Label("Orders appear after Admin confirms an allocation."));
        workspaceStatus.setText("Demo session · requests stay in this session. Allocation is managed by Admin in the connected workspace.");
    }
    @FXML private void createDemand(){SceneNavigator.show(buyerRoot,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
    @FXML private void myDemands(){SceneNavigator.replace(buyerRoot,"/com/coffeemongers/view/MyDemands.fxml","Coffee Mongers · My Demands");}
    @FXML private void myOrders(){SceneNavigator.replace(buyerRoot,"/com/coffeemongers/view/MyOrders.fxml","Coffee Mongers · My Orders");}
    @FXML private void selectedDemand(){var row=recentDemandsTable.getSelectionModel().getSelectedItem();if(row==null){workspaceStatus.setText("Select a demand row to view its details.");return;}row.open(buyerRoot);}
}
