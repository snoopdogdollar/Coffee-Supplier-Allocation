package com.coffeemongers.controller;

import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.BatchItemAllocation;
import com.coffeeallocation.service.SharedAllocationService;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Locale;

public final class AllocationPreviewController {
    private static final String SELECT_WEEK = "Choose the demand's ISO delivery week and click Load Preview.";
    private static final String ACCESS_REQUIRED = "Sign in as the configured test Admin to view allocation.";

    @FXML private BorderPane previewRoot;
    @FXML private TextField yearField, weekField;
    @FXML private Button loadPreviewButton;
    @FXML private Label previewStatus;
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
    }

    @FXML
    private void goBack() {
        SceneNavigator.back(previewRoot, "/com/coffeemongers/admin/AdminDashboard.fxml",
                "Coffee Mongers · Admin Dashboard");
    }

    private record SupplierProposal(String supplier, long supplyId, BigDecimal quantity) {}
}
