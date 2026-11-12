package com.coffeemongers.controller;

import com.coffeeallocation.model.AdminSupplyView;
import com.coffeeallocation.service.AdminSupplyService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminSupplyController {
    @FXML private BorderPane screenRoot;
    @FXML private TableView<AdminSupplyView.Row> supplyTable;
    @FXML private TextField yearField, weekField, supplierField, productField;
    @FXML private Label loadedWeekLabel, statusLabel;
    @FXML private Button loadWeekButton, refreshButton, loadMoreButton;
    @FXML private CheckBox allWeeksCheckBox;
    private AdminSupplyService service;
    private Filter applied;
    private Long next;
    private boolean busy;
    private record Filter(Integer year, Integer week, String supplier, String product) { }

    @FXML public void initialize() {
        column("Supply ID",r -> Long.toString(r.supplyId()),65);
        column("ISO week",r -> String.format("%04d-W%02d",r.year(),r.week()),100);
        column("Supplier",r -> r.supplierName()+" (#"+r.supplierId()+")",205);
        column("Coffee product",r -> r.productName()+" (#"+r.productId()+")",195);
        column("Unit",AdminSupplyView.Row::unit,45);
        column("Initial",r -> r.initialQuantity().toPlainString(),95);
        column("Committed",r -> r.committedQuantity().toPlainString(),100);
        column("Remaining",r -> r.remainingQuantity().toPlainString(),100);
        column("Price / unit",r -> r.unitPrice().toPlainString(),100);
        column("Supply eligibility",AdminSupplyView.Row::eligibility,180);
        supplyTable.setItems(FXCollections.observableArrayList());
        supplyTable.setPlaceholder(new Label("No weekly supply matches these filters."));
        LocalDate today=LocalDate.now();
        yearField.setText(Integer.toString(today.get(IsoFields.WEEK_BASED_YEAR)));
        weekField.setText(Integer.toString(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        allWeeksCheckBox.selectedProperty().addListener((o,old,selected) -> {
            yearField.setDisable(selected);weekField.setDisable(selected);
            loadWeekButton.setText(selected ? "Load All Weeks" : "Load Week");
            if(service!=null && !busy)loadWeek();
        });
        service=AppServices.liveAdminSupply().orElse(null);
        loadMoreButton.setDisable(true);
        if(service==null) {
            loadWeekButton.setDisable(true);refreshButton.setDisable(true);
            statusLabel.setText("Sign in to the configured PostgreSQL Admin workspace to monitor weekly supply.");
        } else loadWeek();
    }
    private void column(String title,Function<AdminSupplyView.Row,String> value,double width) {
        TableColumn<AdminSupplyView.Row,String> column=new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);supplyTable.getColumns().add(column);
    }
    @FXML private void loadWeek() {
        if(busy || service==null)return;
        final Filter filter;
        try {
            filter=allWeeksCheckBox.isSelected()
                    ? new Filter(null,null,supplierField.getText().strip(),productField.getText().strip())
                    : new Filter(Integer.parseInt(yearField.getText().strip()),Integer.parseInt(weekField.getText().strip()),
                            supplierField.getText().strip(),productField.getText().strip());
        } catch(NumberFormatException exception) { statusLabel.setText("Enter whole numbers for ISO year and week.");return; }
        runTask("Loading weekly supply…",() -> fetch(filter,null),page -> {
            applied=filter;next=page.nextBeforeId();supplyTable.getItems().setAll(page.rows());
            loadedWeekLabel.setText((filter.year()==null ? "Showing all ISO weeks" : String.format("Showing %04d-W%02d",filter.year(),filter.week()))
                    +(filter.supplier().isEmpty()?"":" · Supplier: "+filter.supplier())
                    +(filter.product().isEmpty()?"":" · Product: "+filter.product()));
            updateStatus();
        });
    }
    @FXML private void loadMore() {
        if(busy || service==null || applied==null || next==null)return;
        Filter filter=applied;Long before=next;
        runTask("Loading more weekly supply…",() -> fetch(filter,before),page -> {
            supplyTable.getItems().addAll(page.rows());next=page.nextBeforeId();updateStatus();
        });
    }
    private void updateStatus() {
        loadMoreButton.setDisable(next==null);
        statusLabel.setText("Loaded "+supplyTable.getItems().size()+" supply records from PostgreSQL. "
                +(next==null ? "All matching records loaded." : "More matching records are available; click Load More."));
    }
    private AdminSupplyView.Page fetch(Filter filter,Long before) throws Exception {
        return filter.year()==null ? service.allWeeks(filter.supplier(),filter.product(),before)
                : service.page(filter.year(),filter.week(),filter.supplier(),filter.product(),before);
    }
    private <T> void runTask(String notice,Callable<T> action,Consumer<T> success) {
        if(busy || AppServices.liveAdminSupply().isEmpty())return;
        var user=AppServices.currentUser();busy=true;screenRoot.setDisable(true);statusLabel.setText(notice);
        Task<T> task=new Task<>() { @Override protected T call() throws Exception {return action.call();} };
        task.setOnSucceeded(event -> {busy=false;screenRoot.setDisable(false);
            if(AppServices.currentUser().equals(user)&&AppServices.liveAdminSupply().isPresent())success.accept(task.getValue());});
        task.setOnFailed(event -> {busy=false;screenRoot.setDisable(false);
            if(AppServices.currentUser().equals(user)&&AppServices.liveAdminSupply().isPresent())statusLabel.setText(DatabaseMessages.describe(
                    task.getException(),"Could not read weekly supply. Refresh before trying again."));});
        Thread.ofVirtual().name("admin-supply-monitoring").start(task);
    }
    @FXML private void goBack() {
        if(!busy)SceneNavigator.back(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard");
    }
}
