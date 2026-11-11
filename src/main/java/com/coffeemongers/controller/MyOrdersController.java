package com.coffeemongers.controller;

import com.coffeemongers.model.*;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerOrderService;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.model.viewmodel.SupplierAdvertisement;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MyOrdersController {
    @FXML private VBox orderCardsContainer;
    @FXML private Label emptyLabel;
    @FXML private javafx.scene.layout.BorderPane screenRoot;
    @FXML private Label adHeadlineLabel,adMessageLabel;
    @FXML private ImageView adImage;
    @FXML private Button adActionButton;
    @FXML private HBox adBanner;
    @FXML private VBox liveOrderControls;
    @FXML private Label ordersStatusLabel;
    @FXML private Button loadMoreOrdersButton;
    private BuyerOrderService liveOrders;
    private Long nextBeforeId;
    private boolean busy;
    private Window requestWindow;
    private final EventHandler<WindowEvent> blockClose = WindowEvent::consume;
    private List<SupplierAdvertisement> advertisements=List.of();
    private int currentAd;
    private Timeline adRotation;
    @FXML public void initialize() {
        liveOrders = AppServices.liveBuyerOrders().orElse(null);
        if (liveOrders == null) {
            loadOrders();
            setupAdvertisements();
            return;
        }
        adBanner.setVisible(false);
        adBanner.setManaged(false);
        liveOrderControls.setVisible(true);
        liveOrderControls.setManaged(true);
        emptyLabel.setText("This shop has no orders yet.");
        loadPage(null, false, "");
    }

    @FXML
    private void refreshOrders() {
        if (liveOrders != null && !busy) {
            loadPage(null, false, "");
        }
    }

    @FXML
    private void loadMoreOrders() {
        if (liveOrders != null && !busy && nextBeforeId != null) {
            loadPage(nextBeforeId, true, "");
        }
    }

    private void loadPage(Long beforeId, boolean append, String notice) {
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        runLiveTask("Loading orders…", () -> liveOrders.page(beforeId), page -> {
            if (!append) {
                orderCardsContainer.getChildren().clear();
            }
            for (OrderRecord order : page.orders()) {
                addOrder(order);
            }
            nextBeforeId = page.nextBeforeId();
            loadMoreOrdersButton.setDisable(nextBeforeId == null);
            boolean empty = orderCardsContainer.getChildren().isEmpty();
            emptyLabel.setVisible(empty);
            emptyLabel.setManaged(empty);
            ordersStatusLabel.setText(notice.isEmpty()
                    ? "Loaded " + orderCardsContainer.getChildren().size() + " orders for this shop." : notice);
        }, ordersStatusLabel::setText);
    }

    private <T> void runLiveTask(String message, Callable<T> action, Consumer<T> success,
                                 Consumer<String> failure) {
        if (busy || AppServices.liveBuyerOrders().isEmpty()) {
            return;
        }
        var buyer = AppServices.currentUser();
        busy = true;
        screenRoot.setDisable(true);
        ordersStatusLabel.setText(message);
        if (screenRoot.getScene() != null) {
            requestWindow = screenRoot.getScene().getWindow();
            if (requestWindow != null) {
                requestWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            }
        }
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return action.call();
            }
        };
        task.setOnSucceeded(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(buyer) && AppServices.liveBuyerOrders().isPresent()) {
                success.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            finishRequest();
            if (AppServices.currentUser().equals(buyer) && AppServices.liveBuyerOrders().isPresent()) {
                Throwable error = task.getException();
                failure.accept(error instanceof IllegalArgumentException ? error.getMessage()
                        : "Could not complete the request. Refresh orders before trying again.");
            }
        });
        Thread.ofVirtual().name("buyer-orders-request").start(task);
    }

    private void finishRequest() {
        busy = false;
        screenRoot.setDisable(false);
        if (requestWindow != null) {
            requestWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, blockClose);
            requestWindow = null;
        }
    }
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
        if (liveOrders != null) {
            card.setId("order-" + order.id());
        }
        GridPane info=new GridPane();info.setHgap(14);info.setVgap(8);String[] heads={"Order ID","Demand ID","Supplier","Created","Status"};String[] vals={order.id(),order.demandId(),order.supplierName(),order.createdOn().toString(),order.status()};
        for(int i=0;i<heads.length;i++){VBox field=new VBox(4);Label h=new Label(heads[i]);h.getStyleClass().add("order-field-heading");Label v=new Label(vals[i]);v.getStyleClass().add(i==4?"status-pill":"order-field-value");v.setWrapText(true);field.getChildren().addAll(h,v);info.add(field,i,0);ColumnConstraints col=new ColumnConstraints();col.setPercentWidth(100.0/heads.length);col.setHgrow(Priority.ALWAYS);info.getColumnConstraints().add(col);}
        GridPane items=new GridPane();items.setHgap(12);items.setVgap(8);double[] widths={40,20,20,20};for(double width:widths){ColumnConstraints c=new ColumnConstraints();c.setPercentWidth(width);c.setHgrow(Priority.ALWAYS);items.getColumnConstraints().add(c);}
        addCell(items,"Product",0,0,true);addCell(items,"Quantity",1,0,true);addCell(items,"Unit price",2,0,true);addCell(items,"Total (VND)",3,0,true);
        int row=1;for(OrderItem item:order.items()){addCell(items,item.productName(),0,row,false);addCell(items,item.quantity().stripTrailingZeros().toPlainString()+" "+item.unit(),1,row,false);addCell(items,NumberFormatters.money(item.unitPrice())+" VND/"+item.unit(),2,row,false);addCell(items,NumberFormatters.money(item.totalPrice()),3,row,false);row++;}
        boolean canReview = order.status().equalsIgnoreCase("Completed")
                || (liveOrders == null && order.status().equalsIgnoreCase("Delivered"));
        Button review = new Button(order.reviewed() ? "Review submitted"
                : canReview ? "Review supplier" : "Review after delivery");
        review.getStyleClass().add("outline-button");
        review.setDisable(order.reviewed() || !canReview || AppServices.isReadOnlyWorkspacePreview());
        if (!canReview) {
            review.setTooltip(new Tooltip(liveOrders == null
                    ? "The supplier must mark the order delivered before you can review it."
                    : "Confirm receipt before reviewing this order."));
        }
        review.setOnAction(event -> reviewSupplier(order));
        HBox actions = new HBox(12);
        if (liveOrders != null) {
            review.setId("reviewOrder-" + order.id());
            if ("PENDING".equals(order.status())) {
                Button received = new Button("Confirm received");
                received.setId("completeOrder-" + order.id());
                received.getStyleClass().add("primary-button");
                received.setOnAction(event -> confirmReceived(order));
                actions.getChildren().add(received);
            }
        }
        actions.getChildren().add(review);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(info,new Separator(),items,actions);
        orderCardsContainer.getChildren().add(card);
    }

    private void confirmReceived(OrderRecord order) {
        if (busy || liveOrders == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Confirm receipt of order #" + order.id() + " from " + order.supplierName() + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText("Mark order as COMPLETED");
        confirmation.initOwner(screenRoot.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            runLiveTask("Confirming receipt…", () -> {
                liveOrders.complete(Long.parseLong(order.id()));
                return order.id();
            }, id -> loadPage(null, false, "Order #" + id + " is COMPLETED."),
                    ordersStatusLabel::setText);
        }
    }
    private void addCell(GridPane grid,String text,int col,int row,boolean header){Label label=new Label(text);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(header?"order-table-heading":"order-table-value");grid.add(label,col,row);}
    private void reviewSupplier(OrderRecord order){
        if (busy) return;
        Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle("Review supplier");dialog.setHeaderText("Share feedback for "+order.supplierName());ButtonType submit=new ButtonType("Submit review",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(submit,ButtonType.CANCEL);
        CheckBox quality=new CheckBox("Quality met expectations"), quantity=new CheckBox("Quantity was correct"), onTime=new CheckBox("Delivery was on time");quality.setSelected(true);quantity.setSelected(true);onTime.setSelected(true);TextArea comment=new TextArea();comment.setPromptText("Your experience with this supplier");comment.setPrefRowCount(4);
        VBox form=new VBox(8,quality,quantity,onTime,new Label("Comment"),comment);form.setPadding(new Insets(8));dialog.getDialogPane().setContent(form);
        if (liveOrders != null) {
            dialog.initOwner(screenRoot.getScene().getWindow());
            quality.setId("reviewQuality");
            quantity.setId("reviewQuantity");
            onTime.setId("reviewOnTime");
            comment.setId("reviewComment");
            Label reviewError = new Label();
            reviewError.setWrapText(true);
            reviewError.getStyleClass().add("error-text");
            form.getChildren().add(reviewError);
            dialog.setOnCloseRequest(event -> { if (busy) event.consume(); });
            dialog.setOnShown(event -> {
                Button submitButton = (Button) dialog.getDialogPane().lookupButton(submit);
                submitButton.addEventFilter(ActionEvent.ACTION, action -> {
                    action.consume();
                    String text = comment.getText().strip();
                    if (text.codePointCount(0, text.length()) > 255) {
                        reviewError.setText("Comment must contain at most 255 characters.");
                        return;
                    }
                    dialog.getDialogPane().setDisable(true);
                    reviewError.setText("");
                    boolean qualityOk = quality.isSelected();
                    boolean quantityOk = quantity.isSelected();
                    boolean onTimeOk = onTime.isSelected();
                    runLiveTask("Saving review…",
                            () -> liveOrders.review(Long.parseLong(order.id()), qualityOk,
                                    quantityOk, onTimeOk, text),
                            id -> {
                                dialog.close();
                                loadPage(null, false, "Review #" + id + " saved for order #" + order.id() + ".");
                            }, message -> {
                                dialog.getDialogPane().setDisable(false);
                                reviewError.setText(message);
                                ordersStatusLabel.setText(message);
                            });
                });
            });
            dialog.showAndWait();
            return;
        }
        dialog.showAndWait().filter(submit::equals).ifPresent(button->{try{AppServices.marketplace().submitReview(new SupplierReview(order.supplierId(),order.id(),quality.isSelected(),quantity.isSelected(),onTime.isSelected(),comment.getText().trim(),LocalDate.now()));loadOrders();}catch(Exception e){new Alert(Alert.AlertType.ERROR,e.getMessage()).showAndWait();}});
    }
    @FXML private void goToDashboard(){
        if (busy) return;
        if (liveOrders != null) {
            SceneNavigator.clearAndShow(orderCardsContainer,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");
        } else {
            SceneNavigator.back(orderCardsContainer,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");
        }
    }
    @FXML private void goToCreateDemand(){SceneNavigator.show(orderCardsContainer,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
    private static final class NumberFormatters {static String money(BigDecimal amount){return String.format(Locale.US,"%,.2f",amount);}}
}
