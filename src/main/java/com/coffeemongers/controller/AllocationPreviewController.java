package com.coffeemongers.controller;

import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.BatchItemAllocation;
import com.coffeeallocation.service.SharedAllocationService;
import com.coffeeallocation.service.AllocationConfirmationService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Locale;

public final class AllocationPreviewController {
    private static final String SELECT_WEEK = "Choose the demand's ISO delivery week and click Load Preview.";
    private static final String ACCESS_REQUIRED = "Sign in as the configured test Admin to view allocation.";

    @FXML private BorderPane previewRoot;
    @FXML private TextField yearField, weekField;
    @FXML private Button loadPreviewButton, confirmAllocationButton;
    @FXML private Label previewStatus;
    @FXML private TextArea confirmationResult;
    @FXML private TableView<BatchItemAllocation> demandTable;
    @FXML private TableColumn<BatchItemAllocation, Long> demandIdColumn, itemIdColumn;
    @FXML private TableColumn<BatchItemAllocation, String> shopColumn, productColumn, unitColumn;
    @FXML private TableColumn<BatchItemAllocation, BigDecimal> requiredColumn, proposedColumn,
            unallocatedColumn, shortfallColumn;
    @FXML private TableView<SupplierProposal> supplierTable;
    @FXML private TableColumn<SupplierProposal, String> supplierColumn;
    @FXML private TableColumn<SupplierProposal, Long> supplyIdColumn;
    @FXML private TableColumn<SupplierProposal, BigDecimal> allocatedColumn;

    private BatchAllocationPreview displayed;
    private int displayedYear, displayedWeek;
    private boolean confirming;
    private Window confirmationWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now();
        yearField.setText(Integer.toString(today.get(IsoFields.WEEK_BASED_YEAR)));
        weekField.setText(Integer.toString(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        yearField.textProperty().addListener((obs, old, value) -> resetPreview());
        weekField.textProperty().addListener((obs, old, value) -> resetPreview());
        boolean allowed = AppServices.allocationPreviewEnabled();
        setInputsDisabled(!allowed);
        previewStatus.setText(allowed ? SELECT_WEEK : ACCESS_REQUIRED);

        demandTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        demandIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getDemandId()));
        itemIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getDemandItemId()));
        shopColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getShopName()));
        productColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getProductName()));
        unitColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getUnit()));
        requiredColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getItem().getQuantity()));
        proposedColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getProposedQuantity()));
        unallocatedColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getUnallocatedQuantity()));
        shortfallColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getSupplyShortfall()));
        demandTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showSupplierProposals(selected));

        supplierTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        supplierColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().supplier()));
        supplyIdColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().supplyId()));
        allocatedColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().quantity()));
        resetPreview();
    }

    @FXML
    private void loadPreview() {
        if (!AppServices.allocationPreviewEnabled()) {
            previewStatus.setText(ACCESS_REQUIRED);
            return;
        }
        if (loadPreviewButton.isDisabled()) {
            return;
        }
        resetPreview();
        int year;
        int week;
        try {
            year = Integer.parseInt(yearField.getText().strip());
            week = Integer.parseInt(weekField.getText().strip());
        } catch (NumberFormatException exception) {
            previewStatus.setText("Enter the ISO year and week as whole numbers.");
            return;
        }

        setInputsDisabled(true);
        previewStatus.setText("Reading demands and supply from PostgreSQL…");
        Task<BatchAllocationPreview> load = new Task<>() {
            @Override
            protected BatchAllocationPreview call() throws Exception {
                return new SharedAllocationService().preview(year, week);
            }
        };
        load.setOnSucceeded(event -> {
            if (!AppServices.allocationPreviewEnabled()) {
                resetPreview();
                return;
            }
            setInputsDisabled(false);
            displayed = load.getValue();
            displayedYear = year;
            displayedWeek = week;
            refreshConfirmAction();
            demandTable.getItems().setAll(displayed.getItems());
            String weekLabel = String.format(Locale.ROOT, "%04d-W%02d", year, week);
            if (displayed.getItems().isEmpty()) {
                previewStatus.setText(weekLabel + " · No open, unallocated demand lines.");
            } else {
                previewStatus.setText(weekLabel + " · " + displayed.getItems().size()
                        + " demand lines. Select a line to see its proposed suppliers.");
                demandTable.getSelectionModel().selectFirst();
            }
        });
        load.setOnFailed(event -> {
            if (!AppServices.allocationPreviewEnabled()) {
                resetPreview();
                return;
            }
            setInputsDisabled(false);
            Throwable failure = load.getException();
            previewStatus.setText(failure instanceof IllegalArgumentException
                    ? failure.getMessage()
                    : "Could not load the preview. Check the PostgreSQL test connection and try again.");
        });
        Thread.ofVirtual().name("load-allocation-preview").start(load);
    }

    private void resetPreview() {
        displayed = null;
        confirmAllocationButton.setDisable(true);
        confirmationResult.clear();
        confirmationResult.setVisible(false);
        confirmationResult.setManaged(false);
        demandTable.getItems().clear();
        showSupplierProposals(null);
        previewStatus.setText(AppServices.allocationPreviewEnabled() ? SELECT_WEEK : ACCESS_REQUIRED);
    }

    private void showSupplierProposals(BatchItemAllocation row) {
        supplierTable.getItems().clear();
        if (row == null || displayed == null) {
            supplierTable.setPlaceholder(new Label("Select a demand line."));
            return;
        }
        supplierTable.setPlaceholder(new Label("No supplier allocation proposed for this line."));
        for (var entry : row.getAllocations().entrySet()) {
            var supply = displayed.findSupply(row.getItem().getProductId(), entry.getKey());
            supplierTable.getItems().add(new SupplierProposal(
                    supply.getSupplierName(), supply.getSupplyId(), entry.getValue()));
        }
    }

    private void setInputsDisabled(boolean disabled) {
        yearField.setDisable(disabled);
        weekField.setDisable(disabled);
        loadPreviewButton.setDisable(disabled);
        refreshConfirmAction();
    }

    private void refreshConfirmAction() {
        confirmAllocationButton.setDisable(confirming || loadPreviewButton.isDisabled()
                || !AppServices.allocationPreviewEnabled() || displayed == null
                || displayed.getItems().stream().noneMatch(row -> !row.getAllocations().isEmpty()));
    }

    @FXML
    private void confirmAllocation() {
        if (confirming || !AppServices.allocationPreviewEnabled()
                || confirmAllocationButton.isDisabled() || displayed == null) {
            return;
        }
        BatchAllocationPreview approved = displayed;
        int year = displayedYear;
        int week = displayedWeek;
        var admin = AppServices.currentUser();
        long fullLines = approved.getItems().stream().filter(row -> !row.getAllocations().isEmpty()).count();
        long orderCount = approved.getItems().stream().mapToLong(row -> row.getAllocations().size()).sum();
        String weekLabel = String.format(Locale.ROOT, "%04d-W%02d", year, week);

        confirming = true;
        setInputsDisabled(true);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Confirm all " + fullLines + " fully supplied demand lines in " + weekLabel
                        + " and create " + orderCount + " PENDING orders?\n\n"
                        + "The remaining " + (approved.getItems().size() - fullLines)
                        + " demand lines will stay unallocated. Weekly supply will be deducted.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle("Confirm weekly allocation");
        confirmation.setHeaderText("Confirm the entire weekly proposal");
        confirmation.initOwner(previewRoot.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            finishConfirmation();
            return;
        }
        if (!AppServices.allocationPreviewEnabled() || !AppServices.currentUser().equals(admin)) {
            finishConfirmation();
            resetPreview();
            return;
        }

        resetPreview();
        previewStatus.setText("Confirming " + weekLabel + "…");
        previewRoot.setDisable(true);
        confirmationWindow = previewRoot.getScene().getWindow();
        confirmationWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
        Task<AllocationConfirmationService.ConfirmationResult> task = new Task<>() {
            @Override
            protected AllocationConfirmationService.ConfirmationResult call() throws Exception {
                return new AllocationConfirmationService().confirm(year, week, approved);
            }
        };
        task.setOnSucceeded(event -> {
            finishConfirmation();
            if (!AppServices.allocationPreviewEnabled() || !AppServices.currentUser().equals(admin)) {
                resetPreview();
                return;
            }
            var result = task.getValue();
            previewStatus.setText("Confirmed " + weekLabel + ". Load Preview to see the remaining demand.");
            confirmationResult.setText("Created PENDING order IDs: " + result.orderIds()
                    + "\nAllocated demand IDs: " + result.allocatedDemandIds()
                    + "\nAllocated lines: " + result.allocatedItems()
                    + " · Unallocated lines: " + result.unallocatedItems());
            confirmationResult.setVisible(true);
            confirmationResult.setManaged(true);
        });
        task.setOnFailed(event -> {
            finishConfirmation();
            if (!AppServices.allocationPreviewEnabled() || !AppServices.currentUser().equals(admin)) {
                resetPreview();
                return;
            }
            if (task.getException() instanceof IllegalStateException
                    && "Preview has changed. Display a fresh preview and confirm again."
                            .equals(task.getException().getMessage())) {
                previewStatus.setText("The preview changed. Load a fresh preview and review it before confirming.");
            } else {
                previewStatus.setText("Confirmation could not be verified. Check orders and supply in pgAdmin, then load a fresh preview.");
            }
        });
        Thread.ofVirtual().name("confirm-weekly-allocation").start(task);
    }

    private void finishConfirmation() {
        if (confirmationWindow != null) {
            confirmationWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            confirmationWindow = null;
        }
        confirming = false;
        previewRoot.setDisable(false);
        setInputsDisabled(!AppServices.allocationPreviewEnabled());
    }

    @FXML
    private void goBack() {
        if (confirming) {
            return;
        }
        SceneNavigator.back(previewRoot, "/com/coffeemongers/admin/AdminDashboard.fxml",
                "Coffee Mongers · Admin Dashboard");
    }

    private record SupplierProposal(String supplier, long supplyId, BigDecimal quantity) {}
}
