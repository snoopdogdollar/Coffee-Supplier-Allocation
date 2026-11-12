package com.coffeemongers.controller;

import com.coffeeallocation.model.BusinessPartyDetails;
import com.coffeeallocation.model.BusinessPartyType;
import com.coffeeallocation.service.AdminBusinessPartyService;
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

public final class AdminBusinessPartiesController {
    @FXML private BorderPane screenRoot;
    @FXML private VBox partyForm;
    @FXML private TableView<BusinessPartyDetails> partiesTable;
    @FXML private TableColumn<BusinessPartyDetails, String> idColumn, nameColumn, contactColumn,
            phoneColumn, emailColumn, addressColumn, statusColumn;
    @FXML private TextField searchField, nameField, contactField, phoneField, emailField;
    @FXML private TextArea addressField;
    @FXML private Label sectionLabel, titleLabel, formTitle, statusLabel;
    @FXML private Button addPartyButton, togglePartyButton, refreshPartiesButton;
    private final javafx.collections.ObservableList<BusinessPartyDetails> parties = FXCollections.observableArrayList();
    private BusinessPartyType type;
    private AdminBusinessPartyService service;
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;

    @FXML public void initialize() {
        column(idColumn, p -> Long.toString(p.id()));
        column(nameColumn, BusinessPartyDetails::name);
        column(contactColumn, p -> text(p.contactPerson()));
        column(phoneColumn, p -> text(p.phone()));
        column(emailColumn, p -> text(p.email()));
        column(addressColumn, p -> text(p.address()));
        column(statusColumn, p -> p.active() ? "Active" : "Inactive");
        var view = new FilteredList<>(parties);
        partiesTable.setItems(view);
        partiesTable.setPlaceholder(new Label("No matching records."));
        searchField.textProperty().addListener((o, old, value) -> {
            String query = text(value).strip().toLowerCase(Locale.ROOT);
            view.setPredicate(p -> (p.id() + " " + p.name() + " " + text(p.contactPerson()) + " "
                    + text(p.phone()) + " " + text(p.email()) + " " + text(p.address()) + " "
                    + (p.active() ? "Active" : "Inactive")).toLowerCase(Locale.ROOT).contains(query));
        });
        partiesTable.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> updateToggle());
        partyForm.setDisable(true);
        togglePartyButton.setDisable(true);
        refreshPartiesButton.setDisable(true);
        statusLabel.setText("Open Suppliers or Coffee Shops from the configured PostgreSQL Admin workspace.");
    }

    public void configure(BusinessPartyType type) {
        if (this.type != null || busy) throw new IllegalStateException("The catalog type is already configured.");
        if (type == null) throw new IllegalArgumentException("Choose Suppliers or Coffee Shops.");
        this.type = type;
        String title = type == BusinessPartyType.SUPPLIER ? "Suppliers" : "Coffee Shops";
        String singular = type == BusinessPartyType.SUPPLIER ? "Supplier" : "Coffee Shop";
        sectionLabel.setText(title);
        titleLabel.setText(title);
        formTitle.setText("Add " + singular);
        addPartyButton.setText("Add " + singular);
        service = AppServices.liveAdminParties(type).orElse(null);
        if (service != null) {
            partyForm.setDisable(false);
            refreshPartiesButton.setDisable(false);
            load("");
        }
    }

    private static String text(String value) { return value == null ? "" : value; }
    private static void column(TableColumn<BusinessPartyDetails, String> column, Function<BusinessPartyDetails, String> value) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
    }
    @FXML private void refresh() { if (!busy && service != null) load(""); }
    private void load(String notice) {
        runTask("Loading catalog…", service::load, rows -> {
            parties.setAll(rows);
            updateToggle();
            statusLabel.setText(notice.isEmpty() ? "Loaded " + rows.size() + " records from PostgreSQL." : notice);
        });
    }

    @FXML private void addParty() {
        if (busy || service == null) return;
        String name = nameField.getText(), contact = contactField.getText(), phone = phoneField.getText();
        String email = emailField.getText(), address = addressField.getText();
        runTask("Saving record…", () -> service.add(name, contact, phone, email, address), id -> {
            nameField.clear(); contactField.clear(); phoneField.clear(); emailField.clear(); addressField.clear();
            searchField.clear();
            load("Record #" + id + " saved to PostgreSQL.");
        });
    }

    @FXML private void changeStatus() {
        if (busy || service == null) return;
        var selected = partiesTable.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("Select a record first."); return; }
        String action = selected.active() ? "Deactivate" : "Activate";
        var proceed = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        String detail;
        if (type == BusinessPartyType.SUPPLIER) {
            detail = selected.active() ? "New offerings and supply changes will be blocked, and this supplier will be excluded from new allocation previews."
                    : "Active product offerings and eligible weekly supply will become available for allocation again.";
        } else {
            detail = selected.active() ? "New demands will be blocked, and this shop's demands will be excluded from new allocation previews."
                    : "The shop can submit demands again, and its open demands can appear in allocation previews.";
        }
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                action + " " + selected.name() + " (#" + selected.id() + ")?\n\n" + detail
                        + " Existing demands, supply, orders and reviews are kept.", proceed, ButtonType.CANCEL);
        confirmation.initOwner(screenRoot.getScene().getWindow());
        confirmation.setHeaderText(action + " record");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != proceed) return;
        runTask("Updating status…", () -> { service.changeStatus(selected.id(), selected.active()); return selected.id(); },
                id -> load("Record #" + id + (selected.active() ? " deactivated." : " activated.") + " History is preserved."));
    }

    private void updateToggle() {
        var selected = partiesTable.getSelectionModel().getSelectedItem();
        togglePartyButton.setText(selected == null || selected.active() ? "Deactivate selected" : "Activate selected");
        togglePartyButton.setDisable(service == null || selected == null);
    }

    private <T> void runTask(String notice, Callable<T> action, Consumer<T> success) {
        if (busy || type == null || AppServices.liveAdminParties(type).isEmpty()) return;
        var user = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        statusLabel.setText(notice);
        if (screenRoot.getScene() != null) {
            requestWindow = screenRoot.getScene().getWindow();
            if (requestWindow != null) requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
        }
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(event -> {
            finish();
            if (AppServices.currentUser().equals(user) && AppServices.liveAdminParties(type).isPresent()) success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finish();
            if (AppServices.currentUser().equals(user) && AppServices.liveAdminParties(type).isPresent()) {
                statusLabel.setText(DatabaseMessages.describe(task.getException(), "Could not verify the request. Refresh the catalog before trying again."));
            }
        });
        Thread.ofVirtual().name("admin-business-catalog").start(task);
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
