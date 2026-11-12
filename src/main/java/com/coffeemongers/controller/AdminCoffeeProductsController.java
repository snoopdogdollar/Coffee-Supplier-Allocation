package com.coffeemongers.controller;

import com.coffeeallocation.model.CoffeeProductDetails;
import com.coffeeallocation.service.AdminCoffeeProductService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AdminCoffeeProductsController {
    @FXML private BorderPane screenRoot;
    @FXML private VBox productForm;
    @FXML private TableView<CoffeeProductDetails> productsTable;
    @FXML private TableColumn<CoffeeProductDetails, String> idColumn, nameColumn, unitColumn,
            varietyColumn, descriptionColumn, statusColumn;
    @FXML private TextField searchField, nameField, unitField, varietyField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;
    @FXML private Button addProductButton, toggleProductButton, refreshProductsButton;
    private final javafx.collections.ObservableList<CoffeeProductDetails> products = FXCollections.observableArrayList();
    private AdminCoffeeProductService service;
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;

    @FXML public void initialize() {
        column(idColumn, p -> Long.toString(p.productId()));
        column(nameColumn, CoffeeProductDetails::name);
        column(unitColumn, CoffeeProductDetails::unit);
        column(varietyColumn, p -> text(p.variety()));
        column(descriptionColumn, p -> text(p.description()));
        column(statusColumn, p -> p.active() ? "Active" : "Inactive");
        FilteredList<CoffeeProductDetails> view = new FilteredList<>(products);
        productsTable.setItems(view);
        productsTable.setPlaceholder(new Label("No matching coffee products."));
        searchField.textProperty().addListener((o, old, value) -> {
            String query = text(value).strip().toLowerCase(Locale.ROOT);
            view.setPredicate(p -> (p.productId() + " " + p.name() + " " + p.unit() + " "
                    + text(p.variety()) + " " + text(p.description()) + " " + (p.active() ? "Active" : "Inactive"))
                    .toLowerCase(Locale.ROOT).contains(query));
        });
        productsTable.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> updateToggle());
        service = AppServices.liveAdminProducts().orElse(null);
        toggleProductButton.setDisable(true);
        if (service == null) {
            productForm.setDisable(true);
            refreshProductsButton.setDisable(true);
            statusLabel.setText("Sign in to the configured PostgreSQL Admin workspace to manage coffee products.");
        } else load("");
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static void column(TableColumn<CoffeeProductDetails, String> column, Function<CoffeeProductDetails, String> value) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
    }

    @FXML private void refresh() { if (!busy && service != null) load(""); }

    private void load(String notice) {
        runTask("Loading coffee products…", service::load, result -> {
            products.setAll(result);
            updateToggle();
            statusLabel.setText(notice.isEmpty() ? "Loaded " + result.size() + " coffee products from PostgreSQL." : notice);
        });
    }

    @FXML private void addProduct() {
        if (busy || service == null) return;
        String name = nameField.getText(), unit = unitField.getText();
        String variety = varietyField.getText(), description = descriptionField.getText();
        runTask("Saving coffee product…", () -> service.add(name, description, unit, variety), id -> {
            nameField.clear(); unitField.clear(); varietyField.clear(); descriptionField.clear();
            searchField.clear();
            load("Product #" + id + " saved to PostgreSQL. Suppliers and Buyers can select it when they reload their catalogs.");
        });
    }

    @FXML private void changeStatus() {
        if (busy || service == null) return;
        var selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("Select a coffee product first."); return; }
        String action = selected.active() ? "Deactivate" : "Activate";
        var proceed = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        String detail = selected.active()
                ? "It will be unavailable for new demands, supplier offerings and allocation previews. Existing supply and order history are kept."
                : "It will be available for new demands and supplier offerings. Existing active supplier links and their weekly supply will become eligible again.";
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                action + " " + selected.name() + " (#" + selected.productId() + ")?\n\n" + detail,
                proceed, ButtonType.CANCEL);
        confirmation.setHeaderText(action + " coffee product");
        confirmation.initOwner(screenRoot.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != proceed) return;
        runTask("Updating coffee product…", () -> {
            service.changeStatus(selected.productId(), selected.active()); return selected.productId();
        }, id -> load("Product #" + id + (selected.active() ? " deactivated." : " activated.") + " History is preserved."));
    }

    private void updateToggle() {
        var selected = productsTable.getSelectionModel().getSelectedItem();
        toggleProductButton.setText(selected == null || selected.active() ? "Deactivate selected" : "Activate selected");
        toggleProductButton.setDisable(service == null || selected == null);
    }

    private <T> void runTask(String notice, Callable<T> action, Consumer<T> success) {
        if (busy || AppServices.liveAdminProducts().isEmpty()) return;
        var user = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        statusLabel.setText(notice);
        if (screenRoot.getScene() != null) {
            requestWindow = screenRoot.getScene().getWindow();
            if (requestWindow != null) requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
        }
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(event -> {
            finish();
            if (AppServices.currentUser().equals(user) && AppServices.liveAdminProducts().isPresent()) success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finish();
            if (AppServices.currentUser().equals(user) && AppServices.liveAdminProducts().isPresent()) {
                statusLabel.setText(DatabaseMessages.describe(task.getException(),
                        "Could not verify the request. Refresh the catalog before trying again."));
            }
        });
        Thread.ofVirtual().name("admin-coffee-products").start(task);
    }

    private void finish() {
        busy = false;
        screenRoot.setDisable(false);
        if (requestWindow != null) {
            requestWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            requestWindow = null;
        }
    }

    @FXML private void goBack() {
        if (!busy) SceneNavigator.back(screenRoot, "/com/coffeemongers/admin/AdminDashboard.fxml", "Coffee Mongers · Admin Dashboard");
    }
}
