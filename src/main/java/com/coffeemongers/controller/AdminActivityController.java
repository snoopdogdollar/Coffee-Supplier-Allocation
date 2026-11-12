package com.coffeemongers.controller;

import com.coffeeallocation.model.AdminActivityView.*;
import com.coffeeallocation.service.AdminActivityService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminActivityController {
    @FXML private BorderPane screenRoot;
    @FXML private TabPane activityTabs;
    @FXML private TableView<Order> ordersTable;
    @FXML private TableView<Demand> demandsTable;
    @FXML private TextField searchField;
    @FXML private TextArea detailsArea;
    @FXML private Label statusLabel;
    @FXML private Button loadMoreButton, viewDetailsButton, searchButton, refreshButton;
    private AdminActivityService service;
    private Long nextOrder, nextDemand;
    private String appliedQuery = "";
    private boolean busy;

    @FXML public void initialize() {
        addColumn(ordersTable,"Order",o -> Long.toString(o.id()),75);
        addColumn(ordersTable,"Demand",o -> Long.toString(o.demandId()),80);
        addColumn(ordersTable,"Date",o -> o.date().toString(),110);
        addColumn(ordersTable,"Coffee Shop",o -> o.shopName()+" (#"+o.shopId()+")",280);
        addColumn(ordersTable,"Supplier",o -> o.supplierName()+" (#"+o.supplierId()+")",280);
        addColumn(ordersTable,"Status",Order::status,110);
        addColumn(ordersTable,"Lines",o -> Integer.toString(o.itemCount()),70);
        addColumn(ordersTable,"Total",o -> o.total().toPlainString(),140);
        addColumn(demandsTable,"Demand",d -> Long.toString(d.id()),80);
        addColumn(demandsTable,"Requested",d -> d.requestDate().toString(),110);
        addColumn(demandsTable,"Required week",d -> week(d),140);
        addColumn(demandsTable,"Coffee Shop",d -> d.shopName()+" (#"+d.shopId()+")",380);
        addColumn(demandsTable,"Status",Demand::status,120);
        addColumn(demandsTable,"Lines",d -> Integer.toString(d.itemCount()),80);
        ordersTable.setItems(FXCollections.observableArrayList());
        demandsTable.setItems(FXCollections.observableArrayList());
        ordersTable.setPlaceholder(new Label("No matching orders."));
        demandsTable.setPlaceholder(new Label("No matching demands."));
        ordersTable.getSelectionModel().selectedItemProperty().addListener((o,old,row) -> selectionChanged());
        demandsTable.getSelectionModel().selectedItemProperty().addListener((o,old,row) -> selectionChanged());
        activityTabs.getSelectionModel().selectedIndexProperty().addListener((o,old,index) -> selectionChanged());
        service = AppServices.liveAdminActivity().orElse(null);
        updateActions();
        if(service == null) {
            searchButton.setDisable(true);refreshButton.setDisable(true);
            statusLabel.setText("Sign in to the configured PostgreSQL Admin workspace to view activity.");
        } else refresh();
    }

    private static <T> void addColumn(TableView<T> table,String title,Function<T,String> value,double width) {
        TableColumn<T,String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);table.getColumns().add(column);
    }
    private static String week(Demand d) { return String.format("%04d-W%02d",d.year(),d.week()); }
    private boolean ordersSelected() { return activityTabs.getSelectionModel().getSelectedIndex()==0; }
    private void selectionChanged() { detailsArea.clear();updateActions(); }
    private void updateActions() {
        loadMoreButton.setDisable(service==null || (ordersSelected()?nextOrder:nextDemand)==null);
        viewDetailsButton.setDisable(service==null || (ordersSelected()
                ? ordersTable.getSelectionModel().getSelectedItem()==null : demandsTable.getSelectionModel().getSelectedItem()==null));
    }
    private String counts() { return "Loaded "+ordersTable.getItems().size()+" orders and "+demandsTable.getItems().size()+" demands."; }

    @FXML private void refresh() {
        if(busy || service==null)return;
        String query=searchField.getText();
        runTask("Loading PostgreSQL activity…",() -> service.load(query), snapshot -> {
            appliedQuery=query==null?"":query.strip();
            nextOrder=snapshot.orders().nextBeforeId();nextDemand=snapshot.demands().nextBeforeId();
            ordersTable.getItems().setAll(snapshot.orders().rows());demandsTable.getItems().setAll(snapshot.demands().rows());
            detailsArea.clear();updateActions();statusLabel.setText(counts()+" Select a row to view its details.");
        });
    }
    @FXML private void loadMore() {
        if(busy || service==null)return;
        if(ordersSelected() && nextOrder!=null) {
            Long cursor=nextOrder;
            runTask("Loading more orders…",() -> service.orders(appliedQuery,cursor),page -> {
                ordersTable.getItems().addAll(page.rows());nextOrder=page.nextBeforeId();updateActions();statusLabel.setText(counts());
            });
        } else if(!ordersSelected() && nextDemand!=null) {
            Long cursor=nextDemand;
            runTask("Loading more demands…",() -> service.demands(appliedQuery,cursor),page -> {
                demandsTable.getItems().addAll(page.rows());nextDemand=page.nextBeforeId();updateActions();statusLabel.setText(counts());
            });
        }
    }
    @FXML private void viewDetails() {
        if(busy || service==null)return;
        if(ordersSelected()) {
            Order selected=ordersTable.getSelectionModel().getSelectedItem();if(selected==null)return;
            runTask("Loading order details…",() -> service.orderDetails(selected.id()),result -> {
                if(result.isEmpty()){detailsArea.setText("This order no longer exists. Refresh the list.");return;}
                var details=result.get();var header=details.summary();
                StringBuilder text=new StringBuilder("Order #"+header.orderId()+" · Demand #"+selected.demandId()+" · "+header.status()
                        +"\n"+header.shopName()+" → "+header.supplierName()+" · "+header.orderDate()+"\n");
                for(var item:details.items())text.append("\n#").append(item.productId()).append(" ").append(item.productName())
                        .append(" · ").append(item.quantity().toPlainString()).append(" ").append(item.unit())
                        .append(" × ").append(item.unitPrice().toPlainString()).append(" = ").append(item.totalPrice().toPlainString());
                text.append("\n\nTotal: ").append(header.totalPrice().toPlainString());
                detailsArea.setText(text.toString());statusLabel.setText("Loaded order #"+selected.id()+". Prices are the saved order prices.");
            });
        } else {
            Demand selected=demandsTable.getSelectionModel().getSelectedItem();if(selected==null)return;
            runTask("Loading demand details…",() -> service.demandDetails(selected.id()),result -> {
                if(result.isEmpty()){detailsArea.setText("This demand no longer exists. Refresh the list.");return;}
                var details=result.get();var header=details.summary();
                StringBuilder text=new StringBuilder("Demand #"+header.id()+" · "+header.status()+" · "+week(header)
                        +"\n"+header.shopName()+" (#"+header.shopId()+") · Requested "+header.requestDate());
                if(header.note()!=null)text.append("\nNote: ").append(header.note());
                for(var item:details.items()) {
                    text.append("\n\n#").append(item.productId()).append(" ").append(item.productName()).append(" · ")
                            .append(item.quantity().toPlainString()).append(" ").append(item.unit());
                    if(item.note()!=null)text.append(" · ").append(item.note());
                }
                detailsArea.setText(text.toString());statusLabel.setText("Loaded demand #"+selected.id()+".");
            });
        }
    }
    private <T> void runTask(String message,Callable<T> action,Consumer<T> success) {
        if(busy || AppServices.liveAdminActivity().isEmpty())return;
        var user=AppServices.currentUser();busy=true;screenRoot.setDisable(true);statusLabel.setText(message);
        Task<T> task=new Task<>() { @Override protected T call() throws Exception {return action.call();} };
        task.setOnSucceeded(event -> {busy=false;screenRoot.setDisable(false);
            if(AppServices.currentUser().equals(user)&&AppServices.liveAdminActivity().isPresent())success.accept(task.getValue());});
        task.setOnFailed(event -> {busy=false;screenRoot.setDisable(false);
            if(AppServices.currentUser().equals(user)&&AppServices.liveAdminActivity().isPresent())statusLabel.setText(
                    DatabaseMessages.describe(task.getException(),"Could not load activity. Refresh before trying again."));});
        Thread.ofVirtual().name("admin-activity").start(task);
    }
    @FXML private void goBack() {
        if(!busy)SceneNavigator.back(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard");
    }
}
