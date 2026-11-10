package com.coffeemongers.controller;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerDemandService;
import com.coffeemongers.service.BuyerDemandService.Item;
import com.coffeemongers.service.BuyerDemandService.Product;
import com.coffeemongers.view.SceneNavigator;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateDemandController {
    @FXML private DatePicker requestDatePicker;
    @FXML private TextArea notesField;
    @FXML private VBox productRowsContainer;
    @FXML private Label productErrorLabel, catalogStatusLabel, previewNotice, shopLabel, dateLabel;
    @FXML private Button reviewDemandButton, addProductButton;
    private final List<ProductRow> productRows = new ArrayList<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private boolean liveBuyer;

    @FXML
    public void initialize() {
        requestDatePicker.setValue(LocalDate.now());
        boolean readOnly = AppServices.isReadOnlyWorkspacePreview();
        previewNotice.setVisible(readOnly);
        previewNotice.setManaged(readOnly);
        reviewDemandButton.setDisable(readOnly);
        if (readOnly) {
            reviewDemandButton.setTooltip(new Tooltip("Sign in with a Buyer account to submit this demand. Admin workspace previews are read-only."));
        }

        var service = AppServices.liveBuyerDemands();
        liveBuyer = service.isPresent();
        if (liveBuyer) {
            shopLabel.setText("Test shop linked to your buyer account");
            dateLabel.setText("Required Delivery Date *");
            requestDatePicker.setPromptText("Select a date in the week you need the coffee");
            loadCatalog(service.orElseThrow());
        } else {
            var marketplace = AppServices.marketplace();
            for (String name : marketplace.coffeeProducts()) {
                products.put(name, new Product(0, name, marketplace.coffeeProductUnit(name)));
            }
            addProductRow();
        }
    }

    private void loadCatalog(BuyerDemandService service) {
        addProductButton.setDisable(true);
        reviewDemandButton.setDisable(true);
        showCatalogStatus("Loading active coffee products from PostgreSQL…");
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return service.products();
            }
        };
        task.setOnSucceeded(event -> {
            for (Product product : task.getValue()) {
                products.put(product.name() + " · #" + product.id(), product);
            }
            if (products.isEmpty()) {
                showCatalogStatus("No active coffee products in this database. Check the coffee_products table, then reopen this form.");
                return;
            }
            showCatalogStatus("");
            addProductRow();
            addProductButton.setDisable(false);
            reviewDemandButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            String message = failure instanceof IllegalStateException ? failure.getMessage() : null;
            if ("Missing environment variable: DB_URL".equals(message)
                    || "Missing environment variable: DB_USER".equals(message)
                    || "Missing environment variable: DB_PASSWORD".equals(message)) {
                showCatalogStatus(message + ". Set it in PowerShell and restart the app from the same terminal.");
            } else {
                showCatalogStatus("Could not load coffee products. Check the test database connection in the terminal that started the app.");
            }
        });
        Thread worker = new Thread(task, "buyer-demand-catalog");
        worker.setDaemon(true);
        worker.start();
    }

    private void showCatalogStatus(String message) {
        catalogStatusLabel.setText(message);
        catalogStatusLabel.setVisible(!message.isEmpty());
        catalogStatusLabel.setManaged(!message.isEmpty());
    }

    @FXML
    private void addProductRow() {
        ComboBox<String> product = new ComboBox<>(FXCollections.observableArrayList(products.keySet()));
        product.setPromptText("Select coffee product");
        product.setMaxWidth(Double.MAX_VALUE);
        Label unit = new Label("—");
        unit.getStyleClass().add("small-text");
        product.valueProperty().addListener((obs, old, selected) -> unit.setText(selected == null ? "—" : products.get(selected).unit()));
        TextField quantity = new TextField();
        quantity.setPromptText("Quantity");
        quantity.setMaxWidth(Double.MAX_VALUE);
        TextField note = new TextField();
        note.setPromptText("Product note (optional)");
        note.setMaxWidth(Double.MAX_VALUE);
        Button remove = new Button("Remove");
        remove.getStyleClass().add("remove-button");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER_LEFT);
        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setMinWidth(0);
        grid.getColumnConstraints().setAll(flexible, new ColumnConstraints(100, 100, 100),
                new ColumnConstraints(55, 55, 55), new ColumnConstraints(160, 160, 160),
                new ColumnConstraints(78, 78, 78));
        grid.add(product, 0, 0);
        grid.add(quantity, 1, 0);
        grid.add(unit, 2, 0);
        grid.add(note, 3, 0);
        grid.add(remove, 4, 0);

        ProductRow row = new ProductRow(product, quantity, note);
        productRows.add(row);
        productRowsContainer.getChildren().add(grid);
        product.valueProperty().addListener((obs, old, selected) -> {
            if (selected != null && productRows.stream().anyMatch(other ->
                    other != row && selected.equals(other.product().getValue()))) {
                product.setValue(old);
                showError("Each coffee product can appear only once. Add its full quantity in one row.");
            }
        });
        remove.setOnAction(e -> {
            if (productRows.size() == 1) {
                showError("At least one coffee product is required.");
                return;
            }
            productRows.remove(row);
            productRowsContainer.getChildren().remove(grid);
            productErrorLabel.setText("");
        });
        productErrorLabel.setText("");
    }

    @FXML
    private void submitDemand() {
        productErrorLabel.setText("");
        LocalDate selectedDate = requestDatePicker.getValue();
        String field = liveBuyer ? "Required delivery date" : "Request date";
        if (selectedDate == null || selectedDate.isBefore(LocalDate.now())) {
            showError(field + " must be today or later.");
            return;
        }

        List<DemandItem> items = new ArrayList<>();
        List<Item> liveItems = new ArrayList<>();
        for (int i = 0; i < productRows.size(); i++) {
            ProductRow row = productRows.get(i);
            Product selected = products.get(row.product().getValue());
            if (selected == null) {
                showError("Select a coffee product in row " + (i + 1) + ".");
                return;
            }
            try {
                BigDecimal quantity = new BigDecimal(row.quantity().getText().trim());
                if (quantity.signum() <= 0) {
                    showError("Quantity in row " + (i + 1) + " must be greater than zero.");
                    return;
                }
                String note = row.note().getText().trim();
                items.add(new DemandItem(selected.name(), quantity, selected.unit(), note));
                if (liveBuyer) {
                    liveItems.add(new Item(selected.id(), quantity, note));
                }
            } catch (NumberFormatException ex) {
                showError("Enter a valid positive quantity in row " + (i + 1) + ".");
                return;
            } catch (IllegalArgumentException ex) {
                showError("Row " + (i + 1) + " · " + ex.getMessage());
                return;
            }
        }

        String title = items.stream().map(DemandItem::productName).distinct().collect(Collectors.joining(", "));
        DemandRequest draft = new DemandRequest("", title, liveBuyer ? LocalDate.now() : selectedDate,
                liveBuyer ? "" : "Bếp Cà Phê · Ho Chi Minh City", notesField.getText().trim(), items);
        try {
            SceneNavigator.show(productRowsContainer, "/com/coffeemongers/createdemand/DemandDetails.fxml",
                    "Coffee Mongers · Demand Details", controller -> {
                        var details = (DemandDetailsController) controller;
                        if (liveBuyer) {
                            details.setLiveDraft(draft, selectedDate, liveItems);
                        } else {
                            details.setDemand(draft);
                        }
                    });
        } catch (IllegalStateException ex) {
            showError("Could not open the demand review.");
        }
    }

    private void showError(String message) {
        productErrorLabel.setText(message);
    }

    @FXML
    private void goBack() {
        SceneNavigator.back(productRowsContainer, "/com/coffeemongers/view/BuyerDashboard.fxml",
                "Coffee Mongers · Buyer Dashboard");
    }

    private record ProductRow(ComboBox<String> product, TextField quantity, TextField note) {}
}
