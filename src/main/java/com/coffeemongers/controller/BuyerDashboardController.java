package com.coffeemongers.controller;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.model.SupplierMatch;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerDemandService;
import com.coffeemongers.view.SceneNavigator;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.math.BigDecimal;
import java.util.List;

public class BuyerDashboardController {
    @FXML private ImageView logoImage,sidebarImage,farmImage1,farmImage2,farmImage3;
    @FXML private TableView<DemandRow> recentDemandsTable;
    @FXML private ScrollPane dashboardScroll;
    @FXML private VBox supplierSection, ordersSection, orderStat;
    @FXML private TableColumn<DemandRow,String> demandIdColumn,productColumn,quantityColumn,dateColumn,statusColumn;
    @FXML private TableView<OrderRow> recentOrdersTable;
    @FXML private TableColumn<OrderRow,String> orderIdColumn,orderSupplierColumn,orderProductColumn,orderQuantityColumn,orderStatusColumn;
    @FXML private Label totalDemandsLabel,pendingDemandsLabel,totalOrdersLabel;
    @FXML private Label dashboardSubtitle, demandCountTitle, demandCountCaption, pendingCountTitle, pendingCountCaption;
    @FXML private Label buyerNameLabel,buyerEmailLabel;
    @FXML private Button createDemandButton,ordersNavButton,createDemandNavButton,matchingNavButton,suppliersNavButton,profileNavButton;
    @FXML private Button overviewNavButton;
    @FXML private Button refreshDemandsButton;
    @FXML private Hyperlink selectedDemandLink,viewAllOrdersLink,viewSuppliersLink;
    @FXML private Button supplierProfile1,supplierProfile2,supplierProfile3;

    @FXML public void initialize() {
        loadDashboardImages();
        AppServices.currentUser().ifPresent(this::showUser);
        setupDemandTable();
        overviewNavButton.setOnAction(e -> openOverview());
        createDemandButton.setOnAction(e -> openCreateDemand());
        createDemandNavButton.setOnAction(e -> openCreateDemand());
        selectedDemandLink.setOnAction(e -> openSelectedDemand());

        var integration = AppServices.liveBuyerDemands();
        if (integration.isPresent()) {
            setupTestDashboard(integration.get());
            return;
        }

        setupOrderTable();
        showDemands(AppServices.marketplace().recentDemands().stream().map(DemandRow::new).toList());
        matchingNavButton.setOnAction(e -> openLatestMatching());
        ordersNavButton.setOnAction(e -> openMyOrders());
        viewAllOrdersLink.setOnAction(e -> openMyOrders());
        viewSuppliersLink.setOnAction(e -> openSelectedDemand());
        suppliersNavButton.setOnAction(e -> openSuppliers());
        profileNavButton.setOnAction(e -> openProfile());
        supplierProfile1.setOnAction(e -> showSupplier("Cau Dat Coffee Farm"));
        supplierProfile2.setOnAction(e -> showSupplier("Dak Lak Coffee Co-op"));
        supplierProfile3.setOnAction(e -> showSupplier("Gia Lai Coffee Supply"));
    }

    private void setupTestDashboard(BuyerDemandService service) {
        dashboardSubtitle.setText("PostgreSQL test workspace · Create and review your shop's demands.");
        demandCountTitle.setText("Recent Demands");
        demandCountCaption.setText("Up to 20 requests for this shop");
        pendingCountTitle.setText("Open Demands");
        pendingCountCaption.setText("Open requests in this list");
        for (var node : List.of(matchingNavButton, ordersNavButton, suppliersNavButton, profileNavButton,
                supplierSection, ordersSection, orderStat)) {
            node.setVisible(false);
            node.setManaged(false);
        }
        refreshDemandsButton.setVisible(true);
        refreshDemandsButton.setManaged(true);
        refreshDemandsButton.setOnAction(event -> loadSavedDemands(service));
        loadSavedDemands(service);
    }

    private void loadSavedDemands(BuyerDemandService service) {
        refreshDemandsButton.setDisable(true);
        recentDemandsTable.setPlaceholder(new Label("Loading demands…"));
        Task<List<BuyerDemandService.SavedDemand>> load = new Task<>() {
            @Override protected List<BuyerDemandService.SavedDemand> call() throws Exception {
                return service.recentDemands();
            }
        };
        load.setOnSucceeded(event -> {
            refreshDemandsButton.setDisable(false);
            recentDemandsTable.setPlaceholder(new Label("This shop has no saved demands yet."));
            showDemands(load.getValue().stream().map(DemandRow::new).toList());
        });
        load.setOnFailed(event -> {
            refreshDemandsButton.setDisable(false);
            showDemands(List.of());
            totalDemandsLabel.setText("—");
            pendingDemandsLabel.setText("—");
            recentDemandsTable.setPlaceholder(new Label("Could not load demands. Check the test database and refresh."));
        });
        Thread.ofVirtual().name("load-buyer-demands").start(load);
    }
    private void showUser(DemoUser user){buyerNameLabel.setText(user.fullName());buyerEmailLabel.setText(user.email());}
    private void loadDashboardImages(){loadImage(logoImage,"/images/logo.png");loadImage(sidebarImage,"/images/sidebar-landscape.png");loadImage(farmImage1,"/images/coffee-farm.jpg");loadImage(farmImage2,"/images/coffee-beans.jpg");loadImage(farmImage3,"/images/coffee-farm.jpg");}
    private void loadImage(ImageView view,String path){if(view==null)return;var resource=getClass().getResource(path);if(resource!=null)view.setImage(new Image(resource.toExternalForm(),true));}
    private void setupDemandTable(){
        recentDemandsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        demandIdColumn.setCellValueFactory(new PropertyValueFactory<>("demandId"));productColumn.setCellValueFactory(new PropertyValueFactory<>("product"));quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        recentDemandsTable.setFixedCellSize(40);
        recentDemandsTable.setRowFactory(table -> {
            TableRow<DemandRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDemand(row.getItem());
                }
            });
            return row;
        });
    }

    private void showDemands(List<DemandRow> rows) {
        recentDemandsTable.setItems(FXCollections.observableArrayList(rows));
        recentDemandsTable.setPrefHeight(Math.max(116, Math.min(300, rows.size() * 40 + 38)));
        totalDemandsLabel.setText(Integer.toString(rows.size()));
        pendingDemandsLabel.setText(Long.toString(rows.stream().filter(DemandRow::isOpen).count()));
    }
    private void setupOrderTable(){
        recentOrdersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        orderIdColumn.setCellValueFactory(new PropertyValueFactory<>("orderId"));orderSupplierColumn.setCellValueFactory(new PropertyValueFactory<>("supplier"));orderProductColumn.setCellValueFactory(new PropertyValueFactory<>("product"));orderQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));orderStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        List<OrderRow> rows=AppServices.marketplace().buyerOrders().stream().map(OrderRow::new).toList();recentOrdersTable.setItems(FXCollections.observableArrayList(rows));recentOrdersTable.setFixedCellSize(38);recentOrdersTable.setPrefHeight(Math.max(112,Math.min(280,rows.size()*38+38)));totalOrdersLabel.setText(String.valueOf(rows.size()));
        recentOrdersTable.setPlaceholder(new Label("No confirmed orders in this demo session yet."));
    }
    private void openCreateDemand(){SceneNavigator.show(recentDemandsTable,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
    private void openLatestMatching(){DemandRequest demand=AppServices.marketplace().recentDemands().stream().findFirst().orElse(null);SceneNavigator.show(recentDemandsTable,"/com/coffeemongers/createdemand/MatchingResults.fxml","Coffee Mongers · Matching Results",controller->((MatchingResultsController)controller).setDemand(demand));}
    private void openMyOrders(){SceneNavigator.show(recentDemandsTable,"/com/coffeemongers/view/MyOrders.fxml","Coffee Mongers · My Orders");}
    private void openSelectedDemand() {
        DemandRow row = recentDemandsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            new Alert(Alert.AlertType.INFORMATION, "Select a demand row first to see its details.").showAndWait();
            return;
        }
        openDemand(row);
    }

    private void openDemand(DemandRow row) {
        SceneNavigator.show(recentDemandsTable, "/com/coffeemongers/createdemand/DemandDetails.fxml",
                "Coffee Mongers · Demand Details", controller -> {
                    DemandDetailsController details = (DemandDetailsController) controller;
                    if (row.saved == null) {
                        details.setDemand(row.request());
                    } else {
                        details.setSavedDemand(row.request(), row.saved.status(),
                                row.saved.requiredYear(), row.saved.requiredWeek());
                    }
                });
    }
    private void showSupplier(String supplierName){
        DemandRow selected=recentDemandsTable.getSelectionModel().getSelectedItem();DemandRequest demand=selected==null?AppServices.marketplace().recentDemands().stream().findFirst().orElse(null):selected.request();
        if(demand==null){new Alert(Alert.AlertType.INFORMATION,"Create a demand to see suppliers matched to your products.").showAndWait();return;}
        SupplierMatch match=AppServices.marketplace().recommendations(demand.id()).stream().filter(m->m.supplierName().equals(supplierName)).findFirst().orElse(null);
        String details=match==null?"This supplier has no current demo match for the selected demand.":match.supplierLocation()+"\nProduct: "+match.productName()+"\nAvailable: "+match.availableQuantity()+" "+match.unit()+"\nPrice: "+match.unitPrice()+" VND/"+match.unit()+"\nMatch score: "+match.score()+" / 100";
        Alert profile=new Alert(Alert.AlertType.INFORMATION,details);profile.setTitle("Supplier profile");profile.setHeaderText(supplierName);profile.showAndWait();
    }
    @FXML public void openOverview(){dashboardScroll.setVvalue(0);}
    private void openSuppliers(){SceneNavigator.show(recentDemandsTable,"/com/coffeemongers/view/BuyerSuppliers.fxml","Coffee Mongers · Suppliers");}
    private void openProfile(){SceneNavigator.show(recentDemandsTable,"/com/coffeemongers/view/BuyerProfile.fxml","Coffee Mongers · Buyer Profile");}
    public static final class DemandRow {
        private final DemandRequest request;
        private final BuyerDemandService.SavedDemand saved;

        DemandRow(DemandRequest request) {
            this.request = request;
            this.saved = null;
        }

        DemandRow(BuyerDemandService.SavedDemand saved) {
            this.saved = saved;
            List<DemandItem> items = saved.items().stream()
                    .map(item -> new DemandItem(item.productName(), item.quantity(), item.unit(), item.note()))
                    .toList();
            this.request = new DemandRequest(Long.toString(saved.id()), "Coffee demand", saved.requestDate(),
                    "", saved.note() == null ? "" : saved.note(), items);
        }

        public DemandRequest request(){return request;}
        public String getDemandId(){return request.id();}
        public String getProduct(){return request.items().stream().map(DemandItem::productName).distinct().reduce((a,b)->a+", "+b).orElse("—");}
        public String getQuantity(){return request.items().stream().map(i->i.quantity().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—");}
        public String getRequestDate(){return request.requestDate().toString();}
        public String getStatus(){return saved == null ? "Submitted · demo" : saved.status();}
        private boolean isOpen() { return saved == null || "OPEN".equals(saved.status()); }
    }
    public static final class OrderRow {
        private final OrderRecord order;OrderRow(OrderRecord order){this.order=order;}
        public String getOrderId(){return order.id();}public String getSupplier(){return order.supplierName();}
        public String getProduct(){return order.items().stream().map(i->i.productName()).distinct().reduce((a,b)->a+", "+b).orElse("—");}
        public String getQuantity(){return order.items().stream().map(i->i.quantity().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—");}
        public String getStatus(){return order.status();}
    }
}
