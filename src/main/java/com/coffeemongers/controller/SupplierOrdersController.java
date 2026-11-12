package com.coffeemongers.controller;

import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.SupplierOrderService;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.DatabaseMessages;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.Locale;

public class SupplierOrdersController {
    @FXML private BorderPane screenRoot;
    @FXML private VBox ordersContainer, liveOrderControls;
    @FXML private Label emptyLabel, ordersSubtitle, ordersStatusLabel;
    @FXML private Button loadMoreOrdersButton;
    private SupplierOrderService liveOrders;
    private Long nextBeforeId;
    private boolean busy;

    @FXML
    public void initialize() {
        liveOrders = AppServices.liveSupplierOrders().orElse(null);
        if (liveOrders != null) {
            ordersSubtitle.setText("Orders assigned to your supplier account. Refresh to see Buyer receipt confirmations.");
            emptyLabel.setText("This supplier has no incoming orders yet.");
            liveOrderControls.setVisible(true);
            liveOrderControls.setManaged(true);
        }
        refresh();
    }

    @FXML
    private void refresh() {
        if (busy) return;
        if (liveOrders != null) {
            loadPage(null, false);
            return;
        }
        ordersContainer.getChildren().clear();
        var orders = AppServices.marketplace().supplierOrders();
        emptyLabel.setVisible(orders.isEmpty());
        emptyLabel.setManaged(orders.isEmpty());
        for (OrderRecord order : orders) addOrder(order, null);
    }

    @FXML
    private void loadMore() {
        if (!busy && liveOrders != null && nextBeforeId != null) loadPage(nextBeforeId, true);
    }

    private void loadPage(Long beforeId, boolean append) {
        if (busy || AppServices.liveSupplierOrders().isEmpty()) return;
        var supplier = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        ordersStatusLabel.setText("Reading incoming orders from PostgreSQL…");
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        Task<SupplierOrderService.Page> task = new Task<>() {
            @Override protected SupplierOrderService.Page call() throws Exception {
                return liveOrders.page(beforeId);
            }
        };
        task.setOnSucceeded(event -> {
            busy = false;
            screenRoot.setDisable(false);
            if (!AppServices.currentUser().equals(supplier) || AppServices.liveSupplierOrders().isEmpty()) return;
            var page = task.getValue();
            if (!append) ordersContainer.getChildren().clear();
            for (var row : page.orders()) {
                addOrder(row.order(), row.shopName() + " (#" + row.shopId() + ")");
            }
            nextBeforeId = page.nextBeforeId();
            loadMoreOrdersButton.setDisable(nextBeforeId == null);
            boolean empty = ordersContainer.getChildren().isEmpty();
            emptyLabel.setVisible(empty);
            emptyLabel.setManaged(empty);
            ordersStatusLabel.setText("Loaded " + ordersContainer.getChildren().size() + " orders for this supplier.");
        });
        task.setOnFailed(event -> {
            busy = false;
            screenRoot.setDisable(false);
            if (AppServices.currentUser().equals(supplier) && AppServices.liveSupplierOrders().isPresent()) {
                ordersStatusLabel.setText(DatabaseMessages.describe(task.getException(),
                        "Could not load incoming orders. Check the PostgreSQL connection and refresh."));
            }
        });
        Thread.ofVirtual().name("supplier-incoming-orders").start(task);
    }

    private void addOrder(OrderRecord order, String customer) {
        VBox card = new VBox(10);
        card.getStyleClass().add("section-card");
        if (liveOrders != null) card.setId("supplier-order-" + order.id());
        Label status = new Label(order.status());
        status.getStyleClass().add("status-pill");
        HBox header = new HBox(12, new Label("Order " + order.id()), status);
        header.getStyleClass().add("supplier-name");
        Label demand = new Label("Demand " + order.demandId() + " · " + order.createdOn());
        demand.getStyleClass().add("small-text");
        card.getChildren().addAll(header, demand);
        if (customer != null) card.getChildren().add(new Label("Buyer: " + customer));
        card.getChildren().add(new Separator());

        GridPane products = new GridPane();
        products.setHgap(12);
        products.setVgap(8);
        for (double width : new double[]{40, 20, 20, 20}) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            products.getColumnConstraints().add(column);
        }
        addCell(products, "Product", 0, 0);
        addCell(products, "Quantity", 1, 0);
        addCell(products, "Unit price", 2, 0);
        addCell(products, "Total (VND)", 3, 0);
        int row = 1;
        for (var item : order.items()) {
            addCell(products, item.productName(), 0, row);
            addCell(products, item.quantity().stripTrailingZeros().toPlainString() + " " + item.unit(), 1, row);
            addCell(products, money(item.unitPrice()) + " VND/" + item.unit(), 2, row);
            addCell(products, money(item.totalPrice()), 3, row);
            row++;
        }
        card.getChildren().add(products);
        if (liveOrders == null && order.status().equalsIgnoreCase("Confirmed")) {
            Button delivered = new Button("Mark delivered");
            delivered.getStyleClass().add("primary-button");
            delivered.setDisable(AppServices.isReadOnlyWorkspacePreview());
            delivered.setOnAction(event -> {
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Mark order " + order.id() + " as delivered? The buyer can then submit a supplier review.",
                        ButtonType.CANCEL, ButtonType.OK);
                confirmation.setHeaderText("Update order status");
                if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                try {
                    AppServices.marketplace().markOrderDelivered(order.id());
                    refresh();
                } catch (RuntimeException exception) {
                    new Alert(Alert.AlertType.ERROR, exception.getMessage()).showAndWait();
                }
            });
            card.getChildren().add(delivered);
        }
        ordersContainer.getChildren().add(card);
    }

    private static void addCell(GridPane grid, String text, int column, int row) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(row == 0 ? "order-table-heading" : "order-table-value");
        grid.add(label, column, row);
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    @FXML private void openDashboard() {
        if (busy) return;
        if (liveOrders != null) {
            SceneNavigator.clearAndShow(screenRoot, "/com/coffeemongers/supplier/SupplierDashboard.fxml",
                    "Coffee Mongers · Supplier Dashboard");
        } else {
            SceneNavigator.show(screenRoot, "/com/coffeemongers/supplier/SupplierDashboard.fxml",
                    "Coffee Mongers · Supplier Dashboard");
        }
    }
    @FXML private void goBack() {
        if (busy) return;
        if (liveOrders != null) openDashboard();
        else SceneNavigator.back(screenRoot, "/com/coffeemongers/supplier/SupplierDashboard.fxml",
                "Coffee Mongers · Supplier Dashboard");
    }
    @FXML private void openWeeklySupply() {
        if (!busy) SceneNavigator.show(screenRoot, "/com/coffeemongers/supplier/WeeklySupply.fxml",
                "Coffee Mongers · Weekly Supply");
    }
    @FXML private void openProducts() {
        if (!busy && liveOrders == null) SceneNavigator.show(screenRoot,
                "/com/coffeemongers/supplier/SupplierProducts.fxml", "Coffee Mongers · Supplier Products");
    }
}
