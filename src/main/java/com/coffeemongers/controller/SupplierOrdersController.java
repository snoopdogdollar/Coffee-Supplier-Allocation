package com.coffeemongers.controller;

import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.service.*;
import com.coffeemongers.view.DatabaseMessages;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.Locale;

public class SupplierOrdersController {
    @FXML private BorderPane screenRoot;
    @FXML private SupplierSidebarController supplierSidebarController;
    @FXML private VBox ordersContainer;
    @FXML private Label emptyLabel,ordersSubtitle,ordersStatusLabel;
    @FXML private Button loadMoreOrdersButton;
    private SupplierOrderService liveOrders;
    private Long nextBeforeId;
    private boolean busy;
    @FXML public void initialize(){supplierSidebarController.setActive("orders");liveOrders=AppServices.liveSupplierOrders().orElse(null);refresh();}
    @FXML private void refresh(){if(busy)return;if(liveOrders!=null){loadPage(null,false);return;}ordersContainer.getChildren().clear();var orders=AppServices.marketplace().supplierOrders();for(var order:orders)addOrder(order,"Demo buyer");showEmpty(orders.isEmpty());ordersStatusLabel.setText("Demo session · incoming orders are created by Admin allocation in the connected workspace.");loadMoreOrdersButton.setDisable(true);}
    @FXML private void loadMore(){if(!busy&&liveOrders!=null&&nextBeforeId!=null)loadPage(nextBeforeId,true);}
    private void loadPage(Long before,boolean append){
        if(busy||AppServices.liveSupplierOrders().isEmpty())return;
        var user=AppServices.currentUser();busy=true;screenRoot.setDisable(true);
        if(!append){ordersContainer.getChildren().clear();nextBeforeId=null;loadMoreOrdersButton.setDisable(true);}
        showEmpty(false);ordersStatusLabel.setText("Loading incoming orders…");
        Task<SupplierOrderService.Page> task=new Task<>(){@Override protected SupplierOrderService.Page call()throws Exception{return liveOrders.page(before);}};
        task.setOnSucceeded(event -> {finish();if(!AppServices.currentUser().equals(user)||AppServices.liveSupplierOrders().isEmpty())return;var page=task.getValue();for(var row:page.orders())addOrder(row.order(),row.shopName()+" (#"+row.shopId()+")");nextBeforeId=page.nextBeforeId();loadMoreOrdersButton.setDisable(nextBeforeId==null);showEmpty(ordersContainer.getChildren().isEmpty());ordersStatusLabel.setText("Loaded "+ordersContainer.getChildren().size()+" orders for your supplier account. Buyer confirms receipt.");});
        task.setOnFailed(event -> {finish();if(AppServices.currentUser().equals(user)&&AppServices.liveSupplierOrders().isPresent())ordersStatusLabel.setText(DatabaseMessages.describe(task.getException(),"Could not load incoming orders. Refresh to try again."));});
        Thread.ofVirtual().name("supplier-incoming-orders").start(task);
    }
    private void finish(){busy=false;screenRoot.setDisable(false);}
    private void showEmpty(boolean empty){emptyLabel.setVisible(empty);emptyLabel.setManaged(empty);}
    private void addOrder(OrderRecord order,String customer){
        VBox card=new VBox(14);card.getStyleClass().add("section-card");card.setId("supplier-order-"+order.id());
        Label title=new Label("Order #"+order.id());title.getStyleClass().add("section-title");
        Label status=new Label(order.status());status.getStyleClass().add("status-pill");
        Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);HBox heading=new HBox(12,title,space,status);heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label buyer=new Label(customer);buyer.getStyleClass().add("supplier-name");buyer.setWrapText(true);
        Label reference=new Label("Demand "+order.demandId()+" · Created "+order.createdOn());reference.getStyleClass().add("small-text");
        card.getChildren().addAll(heading,buyer,reference,new Separator());
        GridPane items=new GridPane();items.setHgap(12);items.setVgap(10);for(double width:new double[]{40,20,20,20}){var column=new ColumnConstraints();column.setPercentWidth(width);items.getColumnConstraints().add(column);}
        cell(items,"Product",0,0);cell(items,"Quantity",1,0);cell(items,"Unit price",2,0);cell(items,"Total (VND)",3,0);
        int row=1;for(var item:order.items()){cell(items,item.productName(),0,row);cell(items,item.quantity().stripTrailingZeros().toPlainString()+" "+item.unit(),1,row);cell(items,money(item.unitPrice())+" VND/"+item.unit(),2,row);cell(items,money(item.totalPrice()),3,row);row++;}
        if(order.items().isEmpty()){Label noItems=new Label("No product lines recorded for this historical order.");noItems.getStyleClass().add("small-text");items.add(noItems,0,1,4,1);}
        card.getChildren().add(items);
        Label progress=new Label(order.reviewed()?"Buyer review received":("COMPLETED".equalsIgnoreCase(order.status())?"Receipt confirmed by Buyer · awaiting review":"Order status is updated when Buyer confirms receipt."));progress.getStyleClass().add("small-text");progress.setWrapText(true);card.getChildren().add(progress);ordersContainer.getChildren().add(card);
    }
    private static void cell(GridPane grid,String text,int column,int row){Label label=new Label(text);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(row==0?"order-table-heading":"order-table-value");grid.add(label,column,row);}
    private static String money(BigDecimal amount){return String.format(Locale.US,"%,.2f",amount);}
}