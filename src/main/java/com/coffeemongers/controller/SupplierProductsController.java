package com.coffeemongers.controller;

import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.SupplierSupplyView;
import com.coffeeallocation.service.SupplierProductService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.UndoHistory;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SupplierProductsController {
    @FXML private BorderPane screenRoot;
    @FXML private ListView<String> productList;
    @FXML private ComboBox<String> catalogPicker;
    @FXML private Label messageLabel, catalogTitle;
    @FXML private Button addButton, removeButton;
    private SupplierProductService live;
    private final Map<String, CoffeeProduct> available = new LinkedHashMap<>();
    private final Map<String, SupplierSupplyView.Product> offered = new LinkedHashMap<>();
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;

    @FXML public void initialize() {
        live = AppServices.liveSupplierProducts().orElse(null);
        productList.setPlaceholder(new Label("No active products offered yet."));
        if (live != null) {
            catalogTitle.setText("Your active supplier products");
            removeButton.setText("Stop offering");
            refreshLive("");
        } else {
            catalogPicker.setItems(FXCollections.observableArrayList(AppServices.marketplace().coffeeProducts()));
            boolean readOnly = AppServices.isReadOnlyWorkspacePreview();
            addButton.setDisable(readOnly);
            removeButton.setDisable(readOnly);
            refreshDemo();
        }
    }

    @FXML private void reload() {
        if (busy) return;
        if (live != null) refreshLive(""); else refreshDemo();
    }

    private void refreshDemo() {
        productList.setItems(FXCollections.observableArrayList(AppServices.marketplace().supplierProducts()));
    }

    private void refreshLive(String notice) {
        runTask("Loading supplier products…", live::load, catalog -> {
            available.clear(); offered.clear();
            catalog.available().forEach(p -> available.put(label(p.getName(), p.getProductId(), p.getUnit()), p));
            catalog.offered().forEach(p -> offered.put(label(p.name(), p.productId(), p.unit()), p));
            catalogPicker.setItems(FXCollections.observableArrayList(available.keySet()));
            catalogPicker.setValue(null);
            productList.setItems(FXCollections.observableArrayList(offered.keySet()));
            addButton.setDisable(available.isEmpty());
            removeButton.setDisable(offered.isEmpty());
            messageLabel.setText(notice.isEmpty() ? "Loaded products for " + catalog.supplierName() + "." : notice);
        });
    }

    private static String label(String name, long id, String unit) {
        return name + " · #" + id + " (" + unit + ")";
    }

    @FXML private void addProduct() {
        if (busy) return;
        String name = catalogPicker.getValue();
        if (name == null) { messageLabel.setText("Select an existing coffee product first."); return; }
        if (live != null) {
            CoffeeProduct product = available.get(name);
            if (product == null) { messageLabel.setText("Refresh and choose an active coffee product."); return; }
            runTask("Adding product…", () -> live.add(product.getProductId()), added ->
                    refreshLive(added ? "Product saved to PostgreSQL. You can now declare its Weekly Supply."
                            : "That product is already active in your catalog."));
            return;
        }
        boolean added = AppServices.marketplace().addSupplierProduct(name);
        messageLabel.setText(added ? "Product added to this catalog." : "That product is already in the catalog.");
        if (added) UndoHistory.register(screenRoot, "addition of " + name + " to supplier products",
                () -> AppServices.marketplace().removeSupplierProduct(name), messageLabel::setText);
        catalogPicker.setValue(null);
        refreshDemo();
    }

    @FXML private void removeProduct() {
        if (busy) return;
        String selected = productList.getSelectionModel().getSelectedItem();
        if (selected == null) { messageLabel.setText("Select a listed product first."); return; }
        String question = live == null ? "Remove " + selected + " from your demo catalog?"
                : "Stop offering " + selected + "? Its supply will be excluded from new allocation previews. "
                        + "Existing supply records and orders are kept. Add the product again to resume offering it.";
        var confirm = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText(live == null ? "Remove supplier product" : "Stop offering this product");
        confirm.initOwner(screenRoot.getScene().getWindow());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        if (live != null) {
            var product = offered.get(selected);
            if (product == null) { messageLabel.setText("Refresh and choose a listed product."); return; }
            runTask("Stopping product offering…", () -> {
                live.stopOffering(product.supplierProductId());
                return true;
            }, ignored -> refreshLive("Offering stopped. Existing supply and order history are preserved."));
        } else {
            AppServices.marketplace().removeSupplierProduct(selected);
            UndoHistory.register(screenRoot, "removal of " + selected + " from supplier products",
                    () -> AppServices.marketplace().addSupplierProduct(selected), messageLabel::setText);
            messageLabel.setText(selected + " removed. Press ⌘Z (macOS) or Ctrl+Z to restore it.");
            refreshDemo();
        }
    }

    private <T> void runTask(String notice, Callable<T> action, Consumer<T> success) {
        if (busy || AppServices.liveSupplierProducts().isEmpty()) return;
        var user = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        messageLabel.setText(notice);
        if (screenRoot.getScene() != null) {
            requestWindow = screenRoot.getScene().getWindow();
            if (requestWindow != null) requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
        }
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(user) && AppServices.liveSupplierProducts().isPresent()) success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(user) && AppServices.liveSupplierProducts().isPresent()) {
                messageLabel.setText(DatabaseMessages.describe(task.getException(),
                        "Could not verify the request. Refresh products before trying again."));
            }
        });
        Thread.ofVirtual().name("supplier-products").start(task);
    }

    private void finishRequest() {
        busy = false;
        screenRoot.setDisable(false);
        if (requestWindow != null) {
            requestWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            requestWindow = null;
        }
    }

    @FXML private void openDashboard() {
        if (busy) return;
        if (live != null) SceneNavigator.clearAndShow(screenRoot,
                "/com/coffeemongers/supplier/SupplierDashboard.fxml", "Coffee Mongers · Supplier Dashboard");
        else SceneNavigator.show(screenRoot, "/com/coffeemongers/supplier/SupplierDashboard.fxml", "Coffee Mongers · Supplier Dashboard");
    }
    @FXML private void goBack() {
        if (busy) return;
        if (live != null) openDashboard();
        else SceneNavigator.back(screenRoot, "/com/coffeemongers/supplier/SupplierDashboard.fxml", "Coffee Mongers · Supplier Dashboard");
    }
    @FXML private void openWeeklySupply() {
        if (!busy) SceneNavigator.show(screenRoot, "/com/coffeemongers/supplier/WeeklySupply.fxml", "Coffee Mongers · Weekly Supply");
    }
    @FXML private void openOrders() {
        if (!busy) SceneNavigator.show(screenRoot, "/com/coffeemongers/supplier/SupplierOrders.fxml", "Coffee Mongers · Supplier Orders");
    }
}
