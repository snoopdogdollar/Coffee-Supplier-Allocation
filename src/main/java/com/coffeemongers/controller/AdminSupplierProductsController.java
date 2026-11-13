package com.coffeemongers.controller;

import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.AdminSupplierProductView.*;
import com.coffeeallocation.service.AdminSupplierProductService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminSupplierProductsController {
    @FXML private BorderPane screenRoot;
    @FXML private TableView<Row> offeringsTable;
    @FXML private TextField supplierFilter, productFilter;
    @FXML private ComboBox<String> supplierPicker, productPicker;
    @FXML private HBox offerForm;
    @FXML private Button refreshButton, loadMoreButton, stopButton, offerButton;
    @FXML private Label statusLabel;
    private final Map<String,Supplier> suppliers=new LinkedHashMap<>();
    private final Map<String,CoffeeProduct> products=new LinkedHashMap<>();
    private AdminSupplierProductService service;
    private String appliedSupplier="", appliedProduct="";
    private Long next;
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose=WindowEvent::consume;

    @FXML public void initialize() {
        column("Link ID",r -> Long.toString(r.offeringId()),75);
        column("Supplier",r -> r.supplierName()+" (#"+r.supplierId()+")",255);
        column("Coffee product",r -> r.productName()+" (#"+r.productId()+")",255);
        column("Unit",Row::unit,55);
        column("Offering",r -> r.offeringActive()?"Active":"Stopped",115);
        column("Supplier status",r -> r.supplierActive()?"Active":"Inactive",110);
        column("Product status",r -> r.productActive()?"Active":"Inactive",110);
        column("Weekly supply",r -> r.hasWeeklySupply()?"Declared":"Not declared",145);
        offeringsTable.setItems(FXCollections.observableArrayList());
        offeringsTable.setPlaceholder(new Label("No supplier-product links match these filters."));
        offeringsTable.getSelectionModel().selectedItemProperty().addListener((o,old,row) -> {
            updateStop();
            if(row!=null) {
                supplierPicker.setValue(suppliers.entrySet().stream().filter(e -> e.getValue().id()==row.supplierId()).map(Map.Entry::getKey).findFirst().orElse(null));
                productPicker.setValue(products.entrySet().stream().filter(e -> e.getValue().getProductId()==row.productId()).map(Map.Entry::getKey).findFirst().orElse(null));
            }
        });
        service=AppServices.liveAdminSupplierProducts().orElse(null);
        stopButton.setDisable(true);loadMoreButton.setDisable(true);
        if(service==null) {
            offerForm.setDisable(true);refreshButton.setDisable(true);
            statusLabel.setText("Sign in to the configured PostgreSQL Admin workspace to manage supplier offerings.");
        } else load("");
    }
    private void column(String title,Function<Row,String> value,double width) {
        TableColumn<Row,String> column=new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);offeringsTable.getColumns().add(column);
    }
    @FXML private void refresh() { if(!busy&&service!=null)load(""); }
    private void load(String notice) {
        String supplier=supplierFilter.getText(), product=productFilter.getText();
        runTask("Loading supplier-product catalog…",() -> service.load(supplier,product),snapshot -> {
            appliedSupplier=supplier==null?"":supplier.strip();appliedProduct=product==null?"":product.strip();
            suppliers.clear();products.clear();
            snapshot.suppliers().forEach(s -> suppliers.put(s.name()+" · #"+s.id(),s));
            snapshot.products().forEach(p -> products.put(p.getName()+" · #"+p.getProductId()+" ("+p.getUnit()+")",p));
            supplierPicker.setItems(FXCollections.observableArrayList(suppliers.keySet()));supplierPicker.setValue(null);
            productPicker.setItems(FXCollections.observableArrayList(products.keySet()));productPicker.setValue(null);
            offeringsTable.getItems().setAll(snapshot.page().rows());next=snapshot.page().nextBeforeId();
            offerButton.setDisable(suppliers.isEmpty()||products.isEmpty());updateStop();updatePaging();
            statusLabel.setText(notice.isEmpty()?countMessage():notice+" "+countMessage());
        });
    }
    private String countMessage() {
        return "Loaded "+offeringsTable.getItems().size()+" links. "+(next==null?"All matching links loaded.":"More links are available; click Load More.");
    }
    private void updateStop() {
        var row=offeringsTable.getSelectionModel().getSelectedItem();
        stopButton.setDisable(service==null||row==null||!row.offeringActive()||!row.supplierActive());
    }
    private void updatePaging() { loadMoreButton.setDisable(next==null); }
    @FXML private void loadMore() {
        if(busy||service==null||next==null)return;
        Long cursor=next;
        runTask("Loading more supplier-product links…",() -> service.page(appliedSupplier,appliedProduct,cursor),page -> {
            offeringsTable.getItems().addAll(page.rows());next=page.nextBeforeId();updatePaging();statusLabel.setText(countMessage());
        });
    }
    @FXML private void offerProduct() {
        if(busy||service==null)return;
        Supplier supplier=suppliers.get(supplierPicker.getValue());CoffeeProduct product=products.get(productPicker.getValue());
        if(supplier==null||product==null) {statusLabel.setText("Choose an active supplier and coffee product.");return;}
        runTask("Saving supplier offering…",() -> service.offer(supplier.id(),product.getProductId()),changed -> {
            supplierFilter.setText(supplier.name());productFilter.setText(product.getName());
            load(changed?"Offering saved to PostgreSQL. Declare Weekly Supply separately.":"That supplier already offers this product.");
        });
    }
    @FXML private void stopOffering() {
        if(busy||service==null)return;
        Row row=offeringsTable.getSelectionModel().getSelectedItem();
        if(row==null) {statusLabel.setText("Select an active offering first.");return;}
        if(!row.offeringActive()||!row.supplierActive())return;
        var confirmation=new Alert(Alert.AlertType.CONFIRMATION,"Stop "+row.supplierName()+" (#"+row.supplierId()+") from offering "
                +row.productName()+" (#"+row.productId()+")? Existing supply, allocations, orders and reviews are kept. New allocation previews will exclude its supply.",ButtonType.CANCEL,ButtonType.OK);
        confirmation.initOwner(screenRoot.getScene().getWindow());confirmation.setHeaderText("Stop supplier offering");
        if(confirmation.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        runTask("Stopping supplier offering…",() -> {service.stop(row.supplierId(),row.offeringId());return true;},
                ignored -> load("Offering stopped. Supply and transaction history are preserved."));
    }
    private <T> void runTask(String message,Callable<T> action,Consumer<T> success) {
        if(busy||AppServices.liveAdminSupplierProducts().isEmpty())return;
        var user=AppServices.currentUser();busy=true;screenRoot.setDisable(true);statusLabel.setText(message);
        if(screenRoot.getScene()!=null) {
            requestWindow=screenRoot.getScene().getWindow();
            if(requestWindow!=null)requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,blockClose);
        }
        Task<T> task=new Task<>() { @Override protected T call() throws Exception {return action.call();} };
        task.setOnSucceeded(event -> {finish();if(AppServices.currentUser().equals(user)&&AppServices.liveAdminSupplierProducts().isPresent())success.accept(task.getValue());});
        task.setOnFailed(event -> {finish();if(AppServices.currentUser().equals(user)&&AppServices.liveAdminSupplierProducts().isPresent())statusLabel.setText(DatabaseMessages.describe(
                task.getException(),"Could not verify the request. Refresh the supplier-product catalog before trying again."));});
        Thread.ofVirtual().name("admin-supplier-products").start(task);
    }
    private void finish() {
        busy=false;screenRoot.setDisable(false);
        if(requestWindow!=null) {requestWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,blockClose);requestWindow=null;}
    }
    @FXML private void goBack() {
        if(!busy)SceneNavigator.back(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard");
    }
}
