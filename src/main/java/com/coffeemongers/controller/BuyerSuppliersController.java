package com.coffeemongers.controller;

import com.coffeemongers.model.viewmodel.SupplierDirectoryEntry;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

public final class BuyerSuppliersController {
    @FXML private BorderPane screenRoot;
    @FXML private VBox supplierCards;
    @FXML private Label emptyLabel;
    @FXML public void initialize(){var suppliers=AppServices.marketplace().buyerSupplierDirectory();emptyLabel.setVisible(suppliers.isEmpty());emptyLabel.setManaged(suppliers.isEmpty());for(var supplier:suppliers)supplierCards.getChildren().add(card(supplier));}
    private VBox card(SupplierDirectoryEntry supplier){
        VBox card=new VBox(10);card.getStyleClass().add("section-card");card.setPadding(new Insets(16));
        ImageView image=new ImageView();String imagePath=supplier.availableProducts().stream().anyMatch(product->product.toLowerCase().contains("arabica"))?"/images/coffee-farm.jpg":"/images/coffee-beans.jpg";var url=getClass().getResource(imagePath);if(url!=null)image.setImage(new Image(url.toExternalForm(),true));image.setFitWidth(150);image.setFitHeight(96);image.setPreserveRatio(true);
        Label name=new Label(supplier.name());name.getStyleClass().add("supplier-name");Label location=new Label(supplier.location());location.getStyleClass().add("small-text");Label score=new Label(supplier.matchScore().stripTrailingZeros().toPlainString()+" / 100 supplier score");score.getStyleClass().add("score");Label products=new Label(supplier.availableProducts().isEmpty()?"No products currently available":String.join(" · ",supplier.availableProducts()));products.getStyleClass().add("small-text");products.setWrapText(true);
        Button details=new Button("View supplier profile");details.getStyleClass().add("outline-button");details.setOnAction(event->{Alert profile=new Alert(Alert.AlertType.INFORMATION,"Location: "+supplier.location()+"\nSupplier score: "+supplier.matchScore()+" / 100\nAvailable products: "+(supplier.availableProducts().isEmpty()?"None currently listed":String.join(", ",supplier.availableProducts())));profile.setTitle("Supplier profile");profile.setHeaderText(supplier.name());profile.showAndWait();});
        HBox body=new HBox(16,image,new VBox(7,name,location,score,products,details));HBox.setHgrow(body.getChildren().get(1),Priority.ALWAYS);card.getChildren().add(body);return card;
    }
    @FXML private void goBack(){SceneNavigator.back(screenRoot,"/com/coffeemongers/view/BuyerDashboard.fxml","Coffee Mongers · Buyer Dashboard");}
}
