package com.coffeemongers.controller;

import com.coffeeallocation.model.BuyerWorkspaceView.Page;
import com.coffeemongers.service.*;
import com.coffeemongers.view.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public class MyDemandsController {
    @FXML private BorderPane demandsRoot;
    @FXML private BuyerSidebarController buyerSidebarController;
    @FXML private TableView<BuyerDemandRow> demandsTable;
    @FXML private TableColumn<BuyerDemandRow,String> demandColumn,productColumn,quantityColumn,weekColumn,statusColumn,progressColumn;
    @FXML private Label demandsStatus;
    @FXML private Button loadMoreDemandsButton,refreshDemandsButton;
    private BuyerWorkspaceService service;private Long cursor;private boolean busy,queued,displayed;
    @FXML public void initialize() {
        buyerSidebarController.setActive("demands");
        demandColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id()));productColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().products()));
        quantityColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantities()));weekColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().week()));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));progressColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().progress()));
        statusColumn.setCellFactory(column -> DemandStatusPresentation.cell());
        demandsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        demandsTable.setRowFactory(table -> {var row=new TableRow<BuyerDemandRow>();row.setOnMouseClicked(e -> {if(e.getClickCount()==2&&!row.isEmpty())row.getItem().open(demandsRoot);});return row;});
        service=AppServices.liveBuyerDemands().map(s -> s.workspace()).orElse(null);
        SceneNavigator.onShown(demandsRoot,() -> {if(displayed)refresh();else displayed=true;});refresh();
    }
    @FXML private void refresh(){load(null,false);}
    @FXML private void more(){if(cursor!=null)load(cursor,true);}
    private void load(Long before,boolean append) {
        if(busy){if(!append)queued=true;return;}
        if(service==null){demandsTable.getItems().setAll(AppServices.marketplace().recentDemands().stream().map(BuyerDemandRow::demo).toList());demandsStatus.setText("Demo requests · awaiting allocation by Admin in the connected workspace.");loadMoreDemandsButton.setDisable(true);return;}
        if(AppServices.liveBuyerDemands().isEmpty())return;
        var user=AppServices.currentUser();busy=true;refreshDemandsButton.setDisable(true);loadMoreDemandsButton.setDisable(true);
        if(!append){demandsTable.getItems().clear();cursor=null;}
        demandsStatus.setText("Loading your demands…");
        Task<Page> task=new Task<>(){@Override protected Page call()throws Exception{return service.demands(before);}};
        task.setOnSucceeded(e -> {finish();if(!AppServices.currentUser().equals(user)||AppServices.liveBuyerDemands().isEmpty())return;
            demandsTable.getItems().addAll(task.getValue().demands().stream().map(BuyerDemandRow::of).toList());cursor=task.getValue().nextBeforeId();loadMoreDemandsButton.setDisable(cursor==null);
            demandsTable.setPlaceholder(new Label("No demands yet. Create your first request."));
            demandsStatus.setText("Showing "+demandsTable.getItems().size()+" demands · newest first. Select a demand to see its allocation details.");refreshQueued();});
        task.setOnFailed(e -> {finish();if(AppServices.currentUser().equals(user)){loadMoreDemandsButton.setDisable(cursor==null);demandsStatus.setText(DatabaseMessages.describe(task.getException(),"Could not load demands. Refresh to try again."));}refreshQueued();});
        Thread.ofVirtual().name("buyer-demands").start(task);
    }
    private void finish(){busy=false;refreshDemandsButton.setDisable(false);}
    private void refreshQueued(){if(queued){queued=false;if(demandsRoot.getScene()!=null&&demandsRoot.getScene().getWindow()!=null)refresh();}}
    @FXML private void details(){var row=demandsTable.getSelectionModel().getSelectedItem();if(row!=null)row.open(demandsRoot);else demandsStatus.setText("Select a demand first.");}
    @FXML private void create(){SceneNavigator.show(demandsRoot,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
}
