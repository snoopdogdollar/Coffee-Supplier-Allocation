package com.coffeemongers.controller;

import com.coffeemongers.model.*;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

public class MatchingResultsController {
    @FXML private Label demandIdLabel, demandTitleLabel, deliveryDateLabel, statusLabel;
    @FXML private VBox noDemandState;
    @FXML private VBox matchesContainer, screenRoot;
    @FXML private Button confirmButton, ordersButton;
    private DemandRequest demand;
    private final Map<String,TextField> allocationFields=new LinkedHashMap<>();
    private final NumberFormat money=NumberFormat.getIntegerInstance(Locale.US);

    public void setDemand(DemandRequest demand){
        this.demand=demand;
        if(demand==null){demandIdLabel.setText("No submitted demand");demandTitleLabel.setText("Create a demand to see supplier recommendations.");deliveryDateLabel.setText("—");matchesContainer.getChildren().clear();noDemandState.setVisible(true);noDemandState.setManaged(true);confirmButton.setDisable(true);statusLabel.setText("Matching Results will appear after you submit a demand.");return;}
        noDemandState.setVisible(false);noDemandState.setManaged(false);demandIdLabel.setText(demand.id());demandTitleLabel.setText(demand.title());deliveryDateLabel.setText(demand.requestDate().toString());
        confirmButton.setDisable(AppServices.isReadOnlyWorkspacePreview());
        matchesContainer.getChildren().clear();allocationFields.clear();
        List<SupplierMatch> matches=AppServices.marketplace().recommendations(demand.id());
        for(DemandItem item:demand.items()){
            VBox card=new VBox(10);card.getStyleClass().add("section-card");
            BigDecimal requested=item.quantity();
            Label heading=new Label(item.productName()+" · requested "+format(requested)+" "+item.unit());heading.getStyleClass().add("section-title");
            Label hint=new Label("Review supplier price, available quantity and score. Allocations must cover the request and stay within available supply.");hint.getStyleClass().add("small-text");hint.setWrapText(true);card.getChildren().addAll(heading,hint);
            for(SupplierMatch match:matches.stream().filter(m->m.productName().equals(item.productName())).toList()) card.getChildren().add(createMatchRow(match));
            if(matches.stream().noneMatch(m->m.productName().equals(item.productName()))){Label empty=new Label("No demo supplier currently lists this product.");empty.getStyleClass().add("empty-state");card.getChildren().add(empty);}
            matchesContainer.getChildren().add(card);
        }
    }
    private HBox createMatchRow(SupplierMatch match){
        VBox identity=new VBox(4);Label name=new Label(match.supplierName());name.getStyleClass().add("supplier-name");Label location=new Label(match.location());location.getStyleClass().add("small-text");identity.getChildren().addAll(name,location);HBox.setHgrow(identity,Priority.ALWAYS);
        VBox score=new VBox(3,new Label(match.score().stripTrailingZeros().toPlainString()+" / 100"),new Label("Match score"));score.getStyleClass().add("score-block");
        VBox supply=new VBox(3,new Label(format(match.availableQuantity())+" "+match.unit()),new Label("Available"));supply.getStyleClass().add("score-block");
        VBox price=new VBox(3,new Label(money.format(match.unitPrice())+" VND/"+match.unit()),new Label("Unit price"));price.getStyleClass().add("score-block");
        TextField allocation=new TextField("0");allocation.setPromptText(match.unit());allocation.setPrefColumnCount(7);allocation.setTooltip(new Tooltip("Quantity to allocate in "+match.unit()));allocationFields.put(match.id(),allocation);
        VBox alloc=new VBox(3,allocation,new Label("Allocate "+match.unit()));alloc.getStyleClass().add("score-block");
        String imagePath=match.productName().toLowerCase(Locale.ROOT).contains("arabica")?"/images/coffee-farm.jpg":"/images/coffee-beans.jpg";
        ImageView photo=new ImageView();var imageUrl=getClass().getResource(imagePath);if(imageUrl!=null)photo.setImage(new Image(imageUrl.toExternalForm(),true));photo.setFitWidth(72);photo.setFitHeight(54);photo.setPreserveRatio(true);photo.setSmooth(true);
        HBox row=new HBox(15,photo,identity,score,supply,price,alloc);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("supplier-row");return row;
    }
    @FXML private void confirmAllocations(){
        try{
            Map<String,BigDecimal> quantities=new LinkedHashMap<>();
            for(var e:allocationFields.entrySet()){BigDecimal value=new BigDecimal(e.getValue().getText().trim());if(value.signum()<0)throw new IllegalArgumentException("Allocation quantities cannot be negative.");quantities.put(e.getKey(),value);}
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Confirm these allocations and create the corresponding demo orders?",ButtonType.CANCEL,ButtonType.OK);confirm.setHeaderText("Confirm supplier allocation");
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            List<OrderRecord> created=AppServices.marketplace().confirmAllocations(demand.id(),quantities);
            statusLabel.setText(created.size()+" demo order(s) created from the confirmed allocations.");confirmButton.setDisable(true);ordersButton.setVisible(true);ordersButton.setManaged(true);
            }catch(NumberFormatException e){statusLabel.setText("Enter a valid allocation quantity for each supplier.");}
        catch(IllegalArgumentException e){statusLabel.setText(e.getMessage());}
    }
    @FXML private void goToDashboard(){SceneNavigator.back(matchesContainer,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");}
    @FXML private void openOrders(){SceneNavigator.show(matchesContainer,"/com/coffeemongers/view/MyOrders.fxml","Coffee Mongers · My Orders");}
    @FXML private void openCreateDemand(){SceneNavigator.show(matchesContainer,"/com/coffeemongers/createdemand/CreateDemand.fxml","Coffee Mongers · Create Demand");}
    private static String format(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
}
