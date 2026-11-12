package com.coffeemongers.controller;

import com.coffeemongers.model.WeeklySupplyRecord;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.SupplierSupplyService;
import com.coffeeallocation.model.SupplierSupplyView;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeemongers.view.UndoHistory;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class SupplierWeeklySupplyController {
    @FXML private TableView<WeeklySupplyRecord> supplyTable;
    @FXML private TableColumn<WeeklySupplyRecord,String> productColumn, weekColumn, availableColumn, priceColumn, remainingColumn;
    @FXML private ComboBox<String> productPicker;
    @FXML private DatePicker weekPicker, weekFilterPicker;
    @FXML private TextField quantityField, priceField;
    @FXML private Label statusLabel, weekLabel, productHelpLabel;
    @FXML private Button saveButton,deleteButton;
    @FXML private BorderPane screenRoot;
    private String editingId;
    private long editingSupplierProductId;
    private SupplierSupplyService liveSupply;
    private final Map<String, SupplierSupplyView.Product> liveProducts = new LinkedHashMap<>();
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;

    @FXML public void initialize() {
        liveSupply = AppServices.liveSupplierSupply().orElse(null);
        saveButton.setDisable(AppServices.isReadOnlyWorkspacePreview());deleteButton.setDisable(AppServices.isReadOnlyWorkspacePreview());
        if (liveSupply == null) {
            productPicker.setItems(FXCollections.observableArrayList(AppServices.marketplace().coffeeProducts()));
        } else {
            saveButton.setDisable(true);
            productHelpLabel.setText("Choose an active product already linked to your supplier account.");
        }
        weekPicker.setValue(currentWeek());
        weekFilterPicker.setValue(currentWeek());
        productColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().productName()));
        weekColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("Week " + c.getValue().week() + " · " + c.getValue().year()));
        availableColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().initialQuantity()) + " " + c.getValue().unit()));
        priceColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().unitPrice()) + " VND/" + c.getValue().unit()));
        remainingColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().remainingQuantity()) + " " + c.getValue().unit()));
        supplyTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> { if (row != null) edit(row); });
        weekFilterPicker.valueProperty().addListener((obs, old, value) -> { if (!busy) refresh(); });
        refresh();
    }
    private LocalDate currentWeek() { return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private LocalDate selectedWeek() { LocalDate value = weekPicker.getValue(); return value == null ? currentWeek() : value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private void refresh() {
        if (weekFilterPicker == null) return;
        LocalDate date = weekFilterPicker.getValue() == null ? currentWeek() : weekFilterPicker.getValue();
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var isoWeek = java.time.temporal.WeekFields.ISO;
        weekLabel.setText("Week " + monday.get(isoWeek.weekOfWeekBasedYear()) + " · " + monday.get(isoWeek.weekBasedYear()));
        if (liveSupply != null) {
            loadLive(monday, "");
            return;
        }
        supplyTable.setItems(FXCollections.observableArrayList(AppServices.marketplace().weeklySupply(monday)));
    }

    @FXML private void reload() {
        if (!busy) refresh();
    }

    private void loadLive(LocalDate date, String notice) {
        runLiveTask("Reading this supplier's weekly supply…", () -> liveSupply.load(date), snapshot -> {
            liveProducts.clear();
            for (var product : snapshot.catalog().products()) {
                liveProducts.put(product.name() + " · #" + product.productId() + " (" + product.unit() + ")", product);
            }
            productPicker.setItems(FXCollections.observableArrayList(liveProducts.keySet()));
            supplyTable.setItems(FXCollections.observableArrayList(snapshot.rows()));
            clear();
            weekPicker.setValue(date);
            var iso = java.time.temporal.WeekFields.ISO;
            weekLabel.setText("Week " + date.get(iso.weekOfWeekBasedYear()) + " · " + date.get(iso.weekBasedYear()));
            statusLabel.setText(liveProducts.isEmpty()
                    ? "No active products are linked to this supplier. Check supplier_products in the test database."
                    : notice.isEmpty() ? "Loaded supply for " + snapshot.catalog().supplierName() + "." : notice);
        });
        // The listener skips a second read while the first request is running.
        weekFilterPicker.setValue(date);
    }

    private <T> void runLiveTask(String message, Callable<T> action, Consumer<T> success) {
        if (busy || AppServices.liveSupplierSupply().isEmpty()) return;
        var supplier = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        statusLabel.setText(message);
        if (screenRoot.getScene() != null) {
            requestWindow = screenRoot.getScene().getWindow();
            if (requestWindow != null) {
                requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            }
        }
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(supplier) && AppServices.liveSupplierSupply().isPresent()) {
                success.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(supplier) && AppServices.liveSupplierSupply().isPresent()) {
                Throwable error = task.getException();
                statusLabel.setText(DatabaseMessages.describe(error,
                        "Could not verify the request. Refresh supply before trying again."));
            }
        });
        Thread.ofVirtual().name("supplier-weekly-supply").start(task);
    }

    private void finishRequest() {
        busy = false;
        screenRoot.setDisable(false);
        if (requestWindow != null) {
            requestWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            requestWindow = null;
        }
    }
    @FXML private void save() {
        if (busy) return;
        try {
            if (liveSupply != null) {
                saveLive();
                return;
            }
            String product = productPicker.getValue();
            if (product == null) throw new IllegalArgumentException("Choose an existing coffee product.");
            BigDecimal quantity = new BigDecimal(quantityField.getText().trim());
            BigDecimal price = new BigDecimal(priceField.getText().trim());
            WeeklySupplyRecord previous=editingId==null?null:supplyTable.getItems().stream().filter(row->row.id().equals(editingId)).findFirst().orElse(null);
            if(previous!=null&&previous.remainingQuantity().compareTo(previous.initialQuantity())<0)previous=null;
            WeeklySupplyRecord saved=AppServices.marketplace().saveWeeklySupply(editingId, product, selectedWeek(), quantity, price);
            if(editingId==null){
                UndoHistory.register(screenRoot,"creation of "+product+" weekly supply",()->{WeeklySupplyRecord current=AppServices.marketplace().weeklySupply(saved.weekStart()).stream().filter(row->row.productName().equals(saved.productName())).findFirst().orElseThrow(()->new IllegalStateException("The supply record no longer exists."));if(current.remainingQuantity().compareTo(current.initialQuantity())<0)throw new IllegalStateException("The supply has already been allocated and cannot be undone safely.");AppServices.marketplace().deleteWeeklySupply(current.id());refresh();},statusLabel::setText);
            }else if(previous!=null){
                WeeklySupplyRecord original=previous;
                UndoHistory.register(screenRoot,"edit to "+original.productName()+" weekly supply",()->{AppServices.marketplace().saveWeeklySupply(saved.id(),original.productName(),original.weekStart(),original.initialQuantity(),original.unitPrice());refresh();},statusLabel::setText);
            }
            clear(); refresh(); statusLabel.setText("Weekly supply saved to this demo session.");
        } catch (NumberFormatException e) { statusLabel.setText("Enter valid numeric quantity and unit price."); }
        catch (IllegalArgumentException e) { statusLabel.setText(e.getMessage()); }
    }
    private void saveLive() {
        long supplierProductId = editingSupplierProductId;
        if (editingId == null) {
            var product = liveProducts.get(productPicker.getValue());
            if (product == null) throw new IllegalArgumentException("Choose one of this supplier's active products.");
            supplierProductId = product.supplierProductId();
        }
        LocalDate week = weekPicker.getValue();
        if (week == null) throw new IllegalArgumentException("Choose a date in the supply week.");
        BigDecimal quantity = new BigDecimal(quantityField.getText().strip());
        BigDecimal price = new BigDecimal(priceField.getText().strip());
        Long id = editingId == null ? null : Long.valueOf(editingId);
        long productId = supplierProductId;
        runLiveTask("Saving weekly supply…",
                () -> liveSupply.save(id, productId, week, quantity, price),
                saved -> loadLive(week, "Supply #" + saved + " saved to PostgreSQL."));
    }

    @FXML private void clear() {
        if (busy) return;
        editingId=null;
        editingSupplierProductId=0;
        productPicker.setValue(null);
        quantityField.clear();
        priceField.clear();
        supplyTable.getSelectionModel().clearSelection();
        weekPicker.setValue(weekFilterPicker.getValue());
        if (liveSupply != null) {
            productPicker.setDisable(liveProducts.isEmpty());
            weekPicker.setDisable(false);
            saveButton.setDisable(liveProducts.isEmpty());
            deleteButton.setDisable(true);
        }
    }
    @FXML private void deleteSelected() {
        if (busy) return;
        WeeklySupplyRecord selected=supplyTable.getSelectionModel().getSelectedItem();
        if(selected==null){statusLabel.setText("Select a weekly supply record first.");return;}
        if (liveSupply != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete unused weekly supply #" + selected.id() + " for " + selected.productName() + "?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.initOwner(screenRoot.getScene().getWindow());
            confirmation.setHeaderText("Delete unused supply");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                LocalDate week = selected.weekStart();
                runLiveTask("Deleting unused supply…", () -> {
                    liveSupply.delete(Long.parseLong(selected.id()));
                    return selected.id();
                }, id -> loadLive(week, "Supply #" + id + " deleted."));
            }
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete the weekly supply record for " + selected.productName() + "?", ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText("Delete supply record");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        AppServices.marketplace().deleteWeeklySupply(selected.id());
        if(selected.remainingQuantity().compareTo(selected.initialQuantity())==0){
            UndoHistory.register(screenRoot,"deletion of "+selected.productName()+" supply",()->AppServices.marketplace().saveWeeklySupply(null,selected.productName(),selected.weekStart(),selected.initialQuantity(),selected.unitPrice()),statusLabel::setText);
            statusLabel.setText("Supply record removed. Press ⌘Z (macOS) or Ctrl+Z to restore it.");
        }else statusLabel.setText("Supply record removed. Undo is unavailable after stock has been allocated.");
        clear(); refresh();
    }
    private void edit(WeeklySupplyRecord row) {
        editingId=row.id();
        editingSupplierProductId=row.supplierProductId();
        String productLabel = liveSupply == null ? row.productName() : liveProducts.entrySet().stream()
                .filter(entry -> entry.getValue().supplierProductId() == row.supplierProductId())
                .map(Map.Entry::getKey).findFirst().orElse(row.productName());
        productPicker.setValue(productLabel);
        quantityField.setText(row.initialQuantity().stripTrailingZeros().toPlainString()); priceField.setText(row.unitPrice().stripTrailingZeros().toPlainString());
        weekPicker.setValue(row.weekStart());
        if (liveSupply != null) {
            productPicker.setDisable(true);
            weekPicker.setDisable(true);
            deleteButton.setDisable(false);
            saveButton.setDisable(false);
        }
        statusLabel.setText("Editing " + row.productName() + " · remaining quantity is allocation controlled.");
    }
    @FXML private void openDashboard() {
        if (busy) return;
        if (liveSupply != null) {
            SceneNavigator.clearAndShow(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");
        } else {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");
        }
    }
    @FXML private void goBack() {
        if (busy) return;
        if (liveSupply != null) openDashboard();
        else SceneNavigator.back(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");
    }
    @FXML private void openOrders() { SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders"); }
    @FXML private void openProducts() { SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products"); }
    @FXML private void openAdmin() { SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard"); }
    private static String format(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
