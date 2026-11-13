package com.coffeemongers.controller;

import com.coffeeallocation.model.AdminReviewView;
import com.coffeeallocation.service.AdminReviewService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminReviewsController {
    @FXML private BorderPane screenRoot;
    @FXML private TableView<AdminReviewView.Row> reviewsTable;
    @FXML private TextField supplierFilter, shopFilter, orderFilter;
    @FXML private TextArea detailsArea;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton, loadMoreButton;
    private AdminReviewService service;
    private Filter applied;
    private Long next;
    private boolean busy;
    private record Filter(String supplier,String shop,Long orderId) { }

    @FXML public void initialize() {
        column("Review",r -> Long.toString(r.reviewId()),70);
        column("Order",r -> Long.toString(r.orderId()),75);
        column("Coffee shop",r -> r.shopName()+" (#"+r.shopId()+")",230);
        column("Supplier",r -> r.supplierName()+" (#"+r.supplierId()+")",230);
        column("Quality OK",r -> yes(r.qualityOk()),90);
        column("Quantity OK",r -> yes(r.quantityOk()),95);
        column("On time OK",r -> yes(r.onTimeOk()),95);
        column("Created",r -> r.createdAt().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")),165);
        column("Order status",AdminReviewView.Row::orderStatus,120);
        reviewsTable.setItems(FXCollections.observableArrayList());reviewsTable.setPlaceholder(new Label("No reviews match these filters."));
        reviewsTable.getSelectionModel().selectedItemProperty().addListener((o,old,row) -> showDetails(row));
        service=AppServices.liveAdminReviews().orElse(null);loadMoreButton.setDisable(true);
        if(service==null) {refreshButton.setDisable(true);statusLabel.setText("Sign in to the configured PostgreSQL Admin workspace to view supplier reviews.");}
        else refresh();
    }
    private void column(String title,Function<AdminReviewView.Row,String> value,double width) {
        TableColumn<AdminReviewView.Row,String> column=new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));column.setPrefWidth(width);reviewsTable.getColumns().add(column);
    }
    private static String yes(boolean value) {return value?"Yes":"No";}
    private void showDetails(AdminReviewView.Row row) {
        if(row==null){detailsArea.clear();return;}
        detailsArea.setText("Review #"+row.reviewId()+" · Order #"+row.orderId()+" · "+row.orderStatus()
                +"\nShop: "+row.shopName()+" (#"+row.shopId()+") · Supplier: "+row.supplierName()+" (#"+row.supplierId()+")"
                +"\nCreated: "+row.createdAt()+"\nQuality OK: "+yes(row.qualityOk())+" · Quantity OK: "+yes(row.quantityOk())+" · On time OK: "+yes(row.onTimeOk())
                +"\n\nComment: "+(row.comment()==null?"No comment.":row.comment()));
    }
    @FXML private void refresh() {
        if(busy||service==null)return;
        final Filter filter;
        try {
            String order=orderFilter.getText().strip();
            filter=new Filter(supplierFilter.getText().strip(),shopFilter.getText().strip(),order.isEmpty()?null:Long.valueOf(order));
        } catch(NumberFormatException exception){statusLabel.setText("Enter a whole-number Order ID, or leave it blank for all orders.");return;}
        runTask("Loading supplier reviews…",() -> service.page(filter.supplier(),filter.shop(),filter.orderId(),null),page -> {
            applied=filter;next=page.nextBeforeId();reviewsTable.getItems().setAll(page.rows());detailsArea.clear();updateStatus();
        });
    }
    @FXML private void loadMore() {
        if(busy||service==null||applied==null||next==null)return;
        Filter filter=applied;Long cursor=next;
        runTask("Loading more supplier reviews…",() -> service.page(filter.supplier(),filter.shop(),filter.orderId(),cursor),page -> {
            reviewsTable.getItems().addAll(page.rows());next=page.nextBeforeId();updateStatus();
        });
    }
    private void updateStatus() {
        loadMoreButton.setDisable(next==null);
        statusLabel.setText("Loaded "+reviewsTable.getItems().size()+" reviews from PostgreSQL. "+(next==null?"All matching reviews loaded.":"More reviews are available; click Load More."));
    }
    private <T> void runTask(String message,Callable<T> action,Consumer<T> success) {
        if(busy||AppServices.liveAdminReviews().isEmpty())return;
        var user=AppServices.currentUser();busy=true;screenRoot.setDisable(true);statusLabel.setText(message);
        Task<T> task=new Task<>() { @Override protected T call() throws Exception {return action.call();} };
        task.setOnSucceeded(event -> {busy=false;screenRoot.setDisable(false);if(AppServices.currentUser().equals(user)&&AppServices.liveAdminReviews().isPresent())success.accept(task.getValue());});
        task.setOnFailed(event -> {busy=false;screenRoot.setDisable(false);if(AppServices.currentUser().equals(user)&&AppServices.liveAdminReviews().isPresent())statusLabel.setText(DatabaseMessages.describe(task.getException(),"Could not load reviews. Refresh before trying again."));});
        Thread.ofVirtual().name("admin-supplier-reviews").start(task);
    }
    @FXML private void goBack() {
        if(!busy)SceneNavigator.back(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard");
    }
}
