package com.coffeemongers.controller;

import com.coffeemongers.model.OrderItem;
import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SupplierOrdersController {
    @FXML private BorderPane screenRoot;
    @FXML private VBox ordersContainer;
    @FXML private Label emptyLabel;
    @FXML public void initialize(){refresh();}
    private void refresh(){ordersContainer.getChildren().clear();var orders=AppServices.marketplace().supplierOrders();emptyLabel.setVisible(orders.isEmpty());emptyLabel.setManaged(orders.isEmpty());for(OrderRecord order:orders){VBox card=new VBox(10);card.getStyleClass().add("section-card");HBox header=new HBox(10,new Label(order.id()),new Label("·"),new Label(order.status()));header.getStyleClass().add("supplier-name");Label demand=new Label("Demand "+order.demandId()+" · "+order.createdOn());demand.getStyleClass().add("small-text");card.getChildren().addAll(header,demand,new Separator());for(OrderItem item:order.items()){HBox row=new HBox(12,new Label(item.productName()),new Region(),new Label(item.quantity().stripTrailingZeros().toPlainString()+" "+item.unit()));HBox.setHgrow(row.getChildren().get(1),Priority.ALWAYS);row.getChildren().get(0).getStyleClass().add("supplier-name");row.getChildren().get(2).getStyleClass().add("score");card.getChildren().add(row);}if(order.status().equalsIgnoreCase("Confirmed")){Button delivered=new Button("Mark delivered");delivered.getStyleClass().add("primary-button");delivered.setDisable(AppServices.isReadOnlyWorkspacePreview());delivered.setOnAction(e->{Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Mark order "+order.id()+" as delivered? The buyer can then submit a supplier review.",ButtonType.CANCEL,ButtonType.OK);confirm.setHeaderText("Update order status");if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;try{AppServices.marketplace().markOrderDelivered(order.id());refresh();}catch(RuntimeException ex){new Alert(Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}});card.getChildren().add(delivered);}ordersContainer.getChildren().add(card);}}
    @FXML private void openDashboard(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");}
    @FXML private void goBack(){SceneNavigator.back(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");}
    @FXML private void openWeeklySupply(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void openProducts(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierProducts.fxml","Coffee Mongers · Supplier Products");}
}
