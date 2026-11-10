package com.coffeemongers.controller;

import com.coffeemongers.model.*;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.model.viewmodel.SupplierAdvertisement;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;

public class MyOrdersController {
    @FXML private VBox orderCardsContainer;
    @FXML private Label emptyLabel;
    @FXML private javafx.scene.layout.BorderPane screenRoot;
    @FXML private Label adHeadlineLabel,adMessageLabel;
    @FXML private ImageView adImage;
    @FXML private Button adActionButton;
    private List<SupplierAdvertisement> advertisements=List.of();
    private int currentAd;
    private Timeline adRotation;
    @FXML public void initialize(){loadOrders();setupAdvertisements();}
    private void setupAdvertisements(){
        advertisements=AppServices.marketplace().featuredSupplierAdvertisements();if(advertisements.isEmpty())return;
        renderAdvertisement(0);adActionButton.setOnAction(e->showAdvertisementDetails(advertisements.get(currentAd)));
        adRotation=new Timeline(new KeyFrame(Duration.seconds(6),event->renderAdvertisement((currentAd+1)%advertisements.size())));adRotation.setCycleCount(Timeline.INDEFINITE);adRotation.play();
    }
    private void renderAdvertisement(int index){currentAd=index;SupplierAdvertisement ad=advertisements.get(index);adHeadlineLabel.setText(ad.headline());adMessageLabel.setText(ad.message());adActionButton.setText(ad.actionText());var resource=getClass().getResource(ad.imageResource());if(resource!=null)adImage.setImage(new Image(resource.toExternalForm(),true));}
    private void showAdvertisementDetails(SupplierAdvertisement ad){Alert details=new Alert(Alert.AlertType.INFORMATION,ad.message()+"\n\nFeatured supplier campaign in the demo marketplace.");details.setTitle("Featured supplier");details.setHeaderText(ad.supplierName());details.showAndWait();}
    private void loadOrders(){orderCardsContainer.getChildren().clear();var orders=AppServices.marketplace().buyerOrders();emptyLabel.setVisible(orders.isEmpty());emptyLabel.setManaged(orders.isEmpty());for(OrderRecord order:orders)addOrder(order);}
    private void addOrder(OrderRecord order){
        VBox card=new VBox(12);card.getStyleClass().add("order-card");card.setPadding(new Insets(18));
        GridPane info=new GridPane();info.setHgap(14);info.setVgap(8);String[] heads={"Order ID","Demand ID","Supplier","Created","Status"};String[] vals={order.id(),order.demandId(),order.supplierName(),order.createdOn().toString(),order.status()};
        for(int i=0;i<heads.length;i++){VBox field=new VBox(4);Label h=new Label(heads[i]);h.getStyleClass().add("order-field-heading");Label v=new Label(vals[i]);v.getStyleClass().add(i==4?"status-pill":"order-field-value");v.setWrapText(true);field.getChildren().addAll(h,v);info.add(field,i,0);ColumnConstraints col=new ColumnConstraints();col.setPercentWidth(100.0/heads.length);col.setHgrow(Priority.ALWAYS);info.getColumnConstraints().add(col);}
        GridPane items=new GridPane();items.setHgap(12);items.setVgap(8);double[] widths={40,20,20,20};for(double width:widths){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(width);c.setHgrow(Priority.ALWAYS);items.getColumnConstraints().add(c);}
        addCell(items,"Product",0,0,true);addCell(items,"Quantity",1,0,true);addCell(items,"Unit price",2,0,true);addCell(items,"Total (VND)",3,0,true);
        int row=1;for(OrderItem item:order.items()){addCell(items,item.productName(),0,row,false);addCell(items,item.quantity().stripTrailingZeros().toPlainString()+" "+item.unit(),1,row,false);addCell(items,NumberFormatters.money(item.unitPrice())+" VND/"+item.unit(),2,row,false);addCell(items,NumberFormatters.money(item.totalPrice()),3,row,false);row++;}
        boolean canReview=order.status().equalsIgnoreCase("Delivered")||order.status().equalsIgnoreCase("Completed");Button review=new Button(order.reviewed()?"Review submitted":canReview?"Review supplier":"Review after delivery");review.getStyleClass().add("outline-button");review.setDisable(order.reviewed()||!canReview||AppServices.isReadOnlyWorkspacePreview());if(!canReview)review.setTooltip(new Tooltip("The supplier must mark the order delivered before you can review it."));review.setOnAction(e->reviewSupplier(order));
        HBox actions=new HBox(review);actions.setAlignment(Pos.CENTER_RIGHT);card.getChildren().addAll(info,new Separator(),items,actions);orderCardsContainer.getChildren().add(card);
    }
    private void addCell(GridPane grid,String text,int col,int row,boolean header){Label label=new Label(text);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(header?"order-table-heading":"order-table-value");grid.add(label,col,row);}
    private void reviewSupplier(OrderRecord order){
        Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle("Review supplier");dialog.setHeaderText("Share feedback for "+order.supplierName());ButtonType submit=new ButtonType("Submit review",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(submit,ButtonType.CANCEL);
        CheckBox quality=new CheckBox("Quality met expectations"), quantity=new CheckBox("Quantity was correct"), onTime=new CheckBox("Delivery was on time");quality.setSelected(true);quantity.setSelected(true);onTime.setSelected(true);TextArea comment=new TextArea();comment.setPromptText("Your experience with this supplier");comment.setPrefRowCount(4);
        VBox form=new VBox(8,quality,quantity,onTime,new Label("Comment"),comment);form.setPadding(new Insets(8));dialog.getDialogPane().setContent(form);
        dialog.showAndWait().filter(submit::equals).ifPresent(button->{try{AppServices.marketplace().submitReview(new SupplierReview(order.supplierId(),order.id(),quality.isSelected(),quantity.isSelected(),onTime.isSelected(),comment.getText().trim(),LocalDate.now()));loadOrders();}catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}});
    }
    @FXML private void goToDashboard(){SceneNavigator.back(orderCardsContainer,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");}
    @FXML private void goToCreateDemand(){SceneNavigator.show(orderCardsContainer,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
    private static final class NumberFormatters {static String money(BigDecimal amount){return String.format(Locale.US,"%,.0f",amount);}}
}
