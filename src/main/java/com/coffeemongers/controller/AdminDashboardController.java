package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import java.util.List;

public class AdminDashboardController {
    @FXML private BorderPane screenRoot;
    @FXML private Button allocationPreviewNav;
    @FXML private TableView<AdminOrderRow> ordersTable;
    @FXML private TableColumn<AdminOrderRow,String> orderColumn,businessColumn,supplierColumn,quantityColumn,statusColumn;
    @FXML private Label totalOrdersLabel,pendingOrdersLabel,activeSuppliersLabel;
    @FXML public void initialize() {
        boolean previewEnabled = AppServices.allocationPreviewEnabled();
        allocationPreviewNav.setVisible(previewEnabled);
        allocationPreviewNav.setManaged(previewEnabled);
        orderColumn.setCellValueFactory(new PropertyValueFactory<>("order"));businessColumn.setCellValueFactory(new PropertyValueFactory<>("business"));supplierColumn.setCellValueFactory(new PropertyValueFactory<>("supplier"));quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        var service=AppServices.marketplace();var orders=service.adminOrders();totalOrdersLabel.setText(Integer.toString(orders.size()));pendingOrdersLabel.setText(Long.toString(orders.stream().filter(o->!o.status().equalsIgnoreCase("completed")&&!o.status().equalsIgnoreCase("cancelled")&&!o.status().equalsIgnoreCase("delivered")).count()));activeSuppliersLabel.setText(Long.toString(service.adminSuppliers().stream().filter(s->s.isActive()).count()));
        ordersTable.setItems(FXCollections.observableArrayList(orders.stream().map(o->new AdminOrderRow(o.id(),"Bếp Cà Phê",o.supplierName(),o.items().stream().map(i->i.quantity().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—"),o.status())).toList()));ordersTable.setPlaceholder(new Label("No demo orders yet. Orders appear here after a buyer confirms allocations."));
    }
    @FXML private void openOverview(){com.coffeemongers.view.WorkspaceRouter.openHome(screenRoot);}
    @FXML private void openActivity(){openManagement("Market Activity");}
    @FXML private void openShops(){openManagement("Coffee Shops");}
    @FXML private void openSuppliers(){openManagement("Suppliers");}
    @FXML private void openProducts(){openManagement("Coffee Products");}
    @FXML private void openSupplierProducts(){openManagement("Supplier Products");}
    @FXML private void openSupply(){openManagement("Supply Monitoring");}
    @FXML private void openReviews(){openManagement("Reviews");}
    @FXML private void openAllocationPreview() {
        if (AppServices.allocationPreviewEnabled()) {
            SceneNavigator.show(screenRoot, "/com/coffeemongers/admin/AllocationPreview.fxml",
                    "Coffee Mongers · Allocation Preview");
        }
    }
    private void openManagement(String section){SceneNavigator.show(screenRoot,"/com/coffeemongers/admin/AdminManagement.fxml","Coffee Mongers · Marketplace Management",controller->((AdminManagementController)controller).selectSection(section));}
    public record AdminOrderRow(String order,String business,String supplier,String quantity,String status) { }
}
