package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeeallocation.service.AdminOverviewService;
import com.coffeeallocation.model.SupplierPerformance;
import com.coffeeallocation.model.WeeklyMarketView;
import com.coffeeallocation.service.WeeklyMarketService;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class AdminDashboardController {
    @FXML private BorderPane screenRoot;
    @FXML private Button allocationPreviewNav;
    @FXML private TableView<AdminOrderRow> ordersTable;
    @FXML private TableColumn<AdminOrderRow,String> orderColumn,businessColumn,supplierColumn,quantityColumn,statusColumn;
    @FXML private Label totalOrdersLabel,pendingOrdersLabel,activeSuppliersLabel;
    @FXML private Label overviewSubtitle,scopeBadge,totalOrdersHint,activeSuppliersHint,recentOrdersHint,overviewNote,overviewStatusLabel;
    @FXML private BarChart<String,Number> weeklyMarketChart;
    @FXML private NumberAxis weeklyQuantityAxis;
    @FXML private ComboBox<WeeklyMarketView.Product> chartProduct;
    @FXML private TextField chartEndYear,chartEndWeek;
    @FXML private Button applyChartButton;
    @FXML private Label weeklyChartStatus,weeklyChartScope;
    @FXML private VBox overviewFooter;
    @FXML private VBox supplierRankingPanel,topSupplierRows;
    @FXML private TableView<SupplierPerformance.Entry> supplierPerformanceTable;
    @FXML private Label rankingStatusLabel,rankingPolicyLabel;
    @FXML private Button refreshOverviewButton;
    private AdminOverviewService liveOverview;
    private boolean busy;
    private boolean displayedOnce,refreshQueued;
    @FXML public void initialize() {
        boolean previewEnabled = AppServices.allocationPreviewEnabled();
        allocationPreviewNav.setVisible(previewEnabled);
        allocationPreviewNav.setManaged(previewEnabled);
        orderColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().order()));
        businessColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().business()));
        supplierColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().supplier()));
        quantityColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().quantity()));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status()));
        ordersTable.setItems(FXCollections.observableArrayList());
        liveOverview=AppServices.liveAdminOverview().orElse(null);
        if(liveOverview!=null) {
            configureLiveOverview();
            // Cached dashboard scenes are reattached when Back restores them.
            screenRoot.sceneProperty().addListener((o,old,scene) -> {
                if(scene!=null) scene.windowProperty().addListener((property,previous,window) -> {
                    if(window!=null&&AppServices.liveAdminOverview().isPresent()) {
                        if(displayedOnce)refreshOverview();
                        else displayedOnce=true;
                    }
                });
            });
        }
        refreshOverview();
    }
    private void configureLiveOverview() {
        supplierRankingPanel.setVisible(true);supplierRankingPanel.setManaged(true);
        rankingPolicyLabel.setText("Quality 40% · Quantity 30% · On-time 30% · Top: active suppliers with at least "
                +com.coffeeallocation.service.SupplierRankingService.MINIMUM_REVIEWS+" reviews");
        addPerformanceColumn("Supplier",190,e -> e.history().supplierName()+" (#"+e.history().supplierId()+")");
        addPerformanceColumn("Score / 100",95,e -> number(e.score()));
        addPerformanceColumn("Quality",85,e -> percent(e.qualityRate()));
        addPerformanceColumn("Quantity",85,e -> percent(e.quantityRate()));
        addPerformanceColumn("On-time",85,e -> percent(e.onTimeRate()));
        addPerformanceColumn("Reviews",70,e -> Long.toString(e.history().reviewCount()));
        addPerformanceColumn("Latest review",140,e -> e.history().latestReviewAt()==null?"—":
                e.history().latestReviewAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        addPerformanceColumn("Status",80,e -> e.history().active()?"Active":"Inactive");
        addPerformanceColumn("Data",150,e -> e.history().reviewCount()==0?"Insufficient data":
                e.history().reviewCount()<com.coffeeallocation.service.SupplierRankingService.MINIMUM_REVIEWS?
                        "Limited data (<"+com.coffeeallocation.service.SupplierRankingService.MINIMUM_REVIEWS+" reviews)":e.topEligible()?"Eligible for Top":"History only");
        supplierPerformanceTable.setPlaceholder(new Label("No supplier performance loaded."));
        overviewFooter.setVisible(true);overviewFooter.setManaged(true);
        overviewSubtitle.setText("PostgreSQL marketplace totals and the latest orders.");
        scopeBadge.setText("All time");totalOrdersHint.setText("All saved orders");activeSuppliersHint.setText("Current supplier status");
        recentOrdersHint.setText("10 latest orders · newest order ID first");
        overviewNote.setText("Totals include historical orders for inactive shops and suppliers. Open Orders & Activity to browse all orders and their details.");
        ordersTable.setPlaceholder(new Label("No orders in PostgreSQL yet. Confirm an allocation to create orders."));
        TableColumn<AdminOrderRow,String> date=new TableColumn<>("Date"),total=new TableColumn<>("Total");
        date.setId("overviewDateColumn");total.setId("overviewTotalColumn");
        date.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().date()));date.setPrefWidth(110);
        total.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().total()));total.setPrefWidth(120);
        orderColumn.setPrefWidth(75);businessColumn.setPrefWidth(220);supplierColumn.setPrefWidth(220);
        quantityColumn.setText("Lines");quantityColumn.setPrefWidth(60);statusColumn.setPrefWidth(110);
        ordersTable.getColumns().setAll(orderColumn,date,businessColumn,supplierColumn,quantityColumn,total,statusColumn);
        ordersTable.setPrefHeight(300);
    }
    @FXML private void refreshOverview() {
        if(busy){refreshQueued=true;return;}
        if(liveOverview==null){loadDemoOverview();return;}
        if(AppServices.liveAdminOverview().isEmpty())return;
        Long productId=chartProduct.getValue()==null?null:chartProduct.getValue().id();
        Integer endYear,endWeek;
        try {
            endYear=chartEndYear.getText().isBlank()?null:Integer.valueOf(chartEndYear.getText().strip());
            endWeek=chartEndWeek.getText().isBlank()?null:Integer.valueOf(chartEndWeek.getText().strip());
            WeeklyMarketService.validate(productId,endYear,endWeek);
        } catch(NumberFormatException exception) {
            weeklyChartStatus.setText("Enter whole numbers for the ending ISO year and week.");return;
        } catch(IllegalArgumentException exception) {
            weeklyChartStatus.setText(exception.getMessage());return;
        }
        var user=AppServices.currentUser();busy=true;
        totalOrdersLabel.setText("—");pendingOrdersLabel.setText("—");activeSuppliersLabel.setText("—");ordersTable.getItems().clear();
        supplierPerformanceTable.getItems().clear();topSupplierRows.getChildren().clear();
        rankingStatusLabel.setText("Loading supplier reviews…");
        weeklyMarketChart.getData().clear();weeklyMarketChart.setVisible(false);weeklyMarketChart.setManaged(false);
        weeklyChartScope.setText("");weeklyChartStatus.setText("Loading weekly supply and demand…");setChartFiltersDisabled(true);
        overviewStatusLabel.setText("Loading PostgreSQL overview…");refreshOverviewButton.setDisable(true);
        Task<com.coffeeallocation.model.AdminOverviewView.Snapshot> task=new Task<>() {
            @Override protected com.coffeeallocation.model.AdminOverviewView.Snapshot call() throws Exception {return liveOverview.load(productId,endYear,endWeek);}
        };
        task.setOnSucceeded(event -> {
            busy=false;refreshOverviewButton.setDisable(false);setChartFiltersDisabled(false);
            if(!AppServices.currentUser().equals(user)||AppServices.liveAdminOverview().isEmpty())return;
            var snapshot=task.getValue();var totals=snapshot.totals();
            totalOrdersLabel.setText(Long.toString(totals.orders()));pendingOrdersLabel.setText(Long.toString(totals.pendingOrders()));
            activeSuppliersLabel.setText(Long.toString(totals.activeSuppliers()));
            showRankings(snapshot);
            showWeeklyMarket(snapshot.weeklyMarket(),false);
            ordersTable.getItems().setAll(snapshot.recentOrders().stream().map(o -> new AdminOrderRow(Long.toString(o.id()),
                    o.shopName()+" (#"+o.shopId()+")",o.supplierName()+" (#"+o.supplierId()+")",Integer.toString(o.itemCount()),
                    o.status(),o.date().toString(),o.total().toPlainString())).toList());
            overviewStatusLabel.setText("Loaded current totals and "+snapshot.recentOrders().size()+" recent orders from PostgreSQL.");
            refreshQueuedIfVisible();
        });
        task.setOnFailed(event -> {
            busy=false;refreshOverviewButton.setDisable(false);setChartFiltersDisabled(false);
            if(AppServices.currentUser().equals(user)&&AppServices.liveAdminOverview().isPresent()) {
                overviewStatusLabel.setText(DatabaseMessages.describe(
                        task.getException(),"Could not load the overview. Refresh before trying again."));
                rankingStatusLabel.setText("Supplier performance unavailable. Refresh to try again.");
                supplierPerformanceTable.setPlaceholder(new Label("Supplier performance unavailable."));
                weeklyChartStatus.setText("Weekly supply and demand unavailable. Refresh to try again.");
            }
            refreshQueuedIfVisible();
        });
        Thread.ofVirtual().name("admin-overview").start(task);
    }
    private void setChartFiltersDisabled(boolean disabled) {
        chartProduct.setDisable(disabled);chartEndYear.setDisable(disabled);chartEndWeek.setDisable(disabled);
        applyChartButton.setDisable(disabled);
    }
    private void showWeeklyMarket(WeeklyMarketView.Chart chart,boolean demo) {
        chartProduct.getItems().setAll(chart.products());chartProduct.setValue(chart.product());
        chartEndYear.setText(Integer.toString(chart.endYear()));chartEndWeek.setText(Integer.toString(chart.endWeek()));
        weeklyMarketChart.getData().clear();
        boolean hasProduct=chart.product()!=null;
        weeklyMarketChart.setVisible(hasProduct);weeklyMarketChart.setManaged(hasProduct);
        if(!hasProduct) {
            weeklyChartScope.setText("No products available.");
            weeklyChartStatus.setText("Add a coffee product to view weekly supply and demand.");return;
        }
        var supply=new XYChart.Series<String,Number>();supply.setName("Supply (declared)");
        var demand=new XYChart.Series<String,Number>();demand.setName("Demand (requested)");
        for(var week:chart.weeks()) {
            addBar(supply,week.label(),week.supply(),chart.product().unit());
            addBar(demand,week.label(),week.demand(),chart.product().unit());
        }
        weeklyQuantityAxis.setLabel("Quantity ("+chart.product().unit()+")");
        weeklyMarketChart.getData().add(supply);weeklyMarketChart.getData().add(demand);
        weeklyChartScope.setText(chart.product().name()+" · "+chart.product().unit()+" · "
                +chart.weeks().getFirst().label()+" to "+chart.weeks().getLast().label());
        boolean empty=chart.weeks().stream().allMatch(w -> w.supply().signum()==0&&w.demand().signum()==0);
        weeklyChartStatus.setText(demo?"Illustrative weekly values · demo only. Sign in as the configured PostgreSQL Admin for saved data.":
                (empty?"No supply or demand recorded for this product in these weeks. ":"")
                +"Supply = total declared quantity. Demand = requested quantity for the delivery week, excluding cancelled demands. "
                +"Includes historical records for inactive businesses and offerings.");
    }
    private void addBar(XYChart.Series<String,Number> series,String week,BigDecimal quantity,String unit) {
        var data=new XYChart.Data<String,Number>(week,quantity.doubleValue());
        data.nodeProperty().addListener((observable,previous,node) -> {
            if(node!=null) {
                String description=week+" · "+series.getName()+": "+quantity.toPlainString()+" "+unit;
                Tooltip.install(node,new Tooltip(description));
            }
        });
        series.getData().add(data);
    }
    private void addPerformanceColumn(String title,double width,Function<SupplierPerformance.Entry,String> value) {
        TableColumn<SupplierPerformance.Entry,String> column=new TableColumn<>(title);
        column.setPrefWidth(width);column.setSortable(false);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        supplierPerformanceTable.getColumns().add(column);
    }
    private static String number(BigDecimal value) {return value==null?"—":value.toPlainString();}
    private static String percent(BigDecimal value) {return value==null?"—":number(value)+"%";}
    private void showRankings(com.coffeeallocation.model.AdminOverviewView.Snapshot snapshot) {
        var rankings=snapshot.rankings();
        supplierPerformanceTable.getItems().setAll(rankings.suppliers());
        supplierPerformanceTable.setPlaceholder(new Label("No suppliers saved yet."));
        if(rankings.topSuppliers().isEmpty()) {
            Label empty=new Label("No eligible Top Suppliers yet. Active suppliers need at least 5 valid reviews.");
            empty.setWrapText(true);empty.getStyleClass().add("small-text");topSupplierRows.getChildren().add(empty);
        }
        int rank=0;
        for(var entry:rankings.topSuppliers()) {
            Label position=new Label(Integer.toString(++rank));position.getStyleClass().add("rank-number");
            Label name=new Label(entry.history().supplierName()+" (#"+entry.history().supplierId()+")");
            name.getStyleClass().add("supplier-name");name.setWrapText(true);
            Label score=new Label(number(entry.score())+"/100 · "+entry.history().reviewCount()+" reviews");
            score.getStyleClass().add("score");
            Label rates=new Label("Quality "+percent(entry.qualityRate())+" · Quantity "+percent(entry.quantityRate())
                    +" · On-time "+percent(entry.onTimeRate()));
            rates.getStyleClass().add("small-text");rates.setWrapText(true);
            VBox details=new VBox(4,name,score,rates);
            HBox row=new HBox(12,position,details);row.getStyleClass().add("supplier-row");
            topSupplierRows.getChildren().add(row);
        }
        rankingStatusLabel.setText("All review history through "+snapshot.reviewCutoff().timestamp()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))+" ("+snapshot.reviewCutoff().timeZone()
                +"). Rates reflect shop feedback on completed orders; each order counts once. "
                +"Suppliers with no reviews have insufficient data; inactive suppliers remain in the table.");
    }
    private void refreshQueuedIfVisible() {
        if(!refreshQueued)return;
        refreshQueued=false;
        if(screenRoot.getScene()!=null&&screenRoot.getScene().getWindow()!=null&&AppServices.liveAdminOverview().isPresent())refreshOverview();
    }
    private void loadDemoOverview() {
        showWeeklyMarket(new WeeklyMarketService().demo(java.time.LocalDate.now()),true);setChartFiltersDisabled(true);
        var service=AppServices.marketplace();var orders=service.adminOrders();totalOrdersLabel.setText(Integer.toString(orders.size()));pendingOrdersLabel.setText(Long.toString(orders.stream().filter(o->!o.status().equalsIgnoreCase("completed")&&!o.status().equalsIgnoreCase("cancelled")&&!o.status().equalsIgnoreCase("delivered")).count()));activeSuppliersLabel.setText(Long.toString(service.adminSuppliers().stream().filter(s->s.isActive()).count()));
        ordersTable.setItems(FXCollections.observableArrayList(orders.stream().map(o->new AdminOrderRow(o.id(),"Bếp Cà Phê",o.supplierName(),o.items().stream().map(i->i.quantity().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—"),o.status())).toList()));ordersTable.setPlaceholder(new Label("No demo orders yet. Orders appear here after a buyer confirms allocations."));
    }
    @FXML private void openOverview(){com.coffeemongers.view.WorkspaceRouter.openHome(screenRoot);}
    @FXML private void openActivity(){
        if (AppServices.liveAdminActivity().isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/OrdersActivity.fxml","Coffee Mongers · Orders & Activity");
        } else openManagement("Market Activity");
    }
    @FXML private void openShops(){openParties(com.coffeeallocation.model.BusinessPartyType.SHOP);}
    @FXML private void openSuppliers(){openParties(com.coffeeallocation.model.BusinessPartyType.SUPPLIER);}
    private void openParties(com.coffeeallocation.model.BusinessPartyType type) {
        String section = type == com.coffeeallocation.model.BusinessPartyType.SHOP ? "Coffee Shops" : "Suppliers";
        if (AppServices.liveAdminParties(type).isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/BusinessParties.fxml","Coffee Mongers · " + section,
                    controller -> ((AdminBusinessPartiesController) controller).configure(type));
        } else openManagement(section);
    }
    @FXML private void openProducts(){
        if (AppServices.liveAdminProducts().isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/CoffeeProducts.fxml","Coffee Mongers · Coffee Products");
        } else openManagement("Coffee Products");
    }
    @FXML private void openSupplierProducts(){
        if(AppServices.liveAdminSupplierProducts().isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/SupplierProducts.fxml","Coffee Mongers · Supplier Products");
        } else openManagement("Supplier Products");
    }
    @FXML private void openSupply(){
        if(AppServices.liveAdminSupply().isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/SupplyMonitoring.fxml","Coffee Mongers · Supply Monitoring");
        } else openManagement("Supply Monitoring");
    }
    @FXML private void openReviews(){
        if(AppServices.liveAdminReviews().isPresent()) {
            SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/SupplierReviews.fxml","Coffee Mongers · Supplier Reviews");
        } else openManagement("Reviews");
    }
    @FXML private void openAllocationPreview() {
        if (AppServices.allocationPreviewEnabled()) {
            SceneNavigator.show(screenRoot, "/com/coffeemongers/admin/AllocationPreview.fxml",
                    "Coffee Mongers · Allocation Preview");
        }
    }
    private void openManagement(String section){SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/AdminManagement.fxml","Coffee Mongers · Marketplace Management",controller->((AdminManagementController)controller).selectSection(section));}
    public record AdminOrderRow(String order,String business,String supplier,String quantity,String status,String date,String total) {
        public AdminOrderRow(String order,String business,String supplier,String quantity,String status) {
            this(order,business,supplier,quantity,status,"","");
        }
    }
}
