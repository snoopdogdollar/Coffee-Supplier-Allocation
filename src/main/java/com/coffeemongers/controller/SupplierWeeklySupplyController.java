package com.coffeemongers.controller;

import com.coffeemongers.model.WeeklySupplyRecord;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.UndoHistory;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class SupplierWeeklySupplyController {
    @FXML private TableView<WeeklySupplyRecord> supplyTable;
    @FXML private TableColumn<WeeklySupplyRecord,String> productColumn, weekColumn, availableColumn, priceColumn, remainingColumn;
    @FXML private ComboBox<String> productPicker;
    @FXML private DatePicker weekPicker, weekFilterPicker;
    @FXML private TextField quantityField, priceField;
    @FXML private Label statusLabel, weekLabel;
    @FXML private Button saveButton,deleteButton;
    @FXML private BorderPane screenRoot;
    private String editingId;

    @FXML public void initialize() {
        saveButton.setDisable(AppServices.isReadOnlyWorkspacePreview());deleteButton.setDisable(AppServices.isReadOnlyWorkspacePreview());
        productPicker.setItems(FXCollections.observableArrayList(AppServices.marketplace().coffeeProducts()));
        weekPicker.setValue(currentWeek());
        weekFilterPicker.setValue(currentWeek());
        productColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().productName()));
        weekColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("Week " + c.getValue().week() + " · " + c.getValue().year()));
        availableColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().initialQuantity()) + " " + c.getValue().unit()));
        priceColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().unitPrice()) + " VND/" + c.getValue().unit()));
        remainingColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(format(c.getValue().remainingQuantity()) + " " + c.getValue().unit()));
        supplyTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> { if (row != null) edit(row); });
        weekFilterPicker.valueProperty().addListener((obs, old, value) -> refresh());
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
        supplyTable.setItems(FXCollections.observableArrayList(AppServices.marketplace().weeklySupply(monday)));
    }
    @FXML private void save() {
        try {
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
    @FXML private void clear() { editingId=null; productPicker.setValue(null); quantityField.clear(); priceField.clear(); supplyTable.getSelectionModel().clearSelection(); weekPicker.setValue(weekFilterPicker.getValue()); }
    @FXML private void deleteSelected() {
        WeeklySupplyRecord selected=supplyTable.getSelectionModel().getSelectedItem();
        if(selected==null){statusLabel.setText("Select a weekly supply record first.");return;}
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
        editingId=row.id(); productPicker.setValue(row.productName()); quantityField.setText(row.initialQuantity().stripTrailingZeros().toPlainString()); priceField.setText(row.unitPrice().stripTrailingZeros().toPlainString());
        statusLabel.setText("Editing " + row.productName() + " · remaining quantity is allocation controlled.");
    }
    @FXML private void openDashboard() { SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard"); }
    @FXML private void goBack() { SceneNavigator.back(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard"); }
    @FXML private void openOrders() { SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders"); }
    @FXML private void openProducts() { SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products"); }
    @FXML private void openAdmin() { SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard"); }
    private static String format(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
