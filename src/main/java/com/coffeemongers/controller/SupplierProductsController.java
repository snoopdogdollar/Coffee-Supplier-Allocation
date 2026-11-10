package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.UndoHistory;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public class SupplierProductsController {
    @FXML private BorderPane screenRoot;
    @FXML private ListView<String> productList;
    @FXML private ComboBox<String> catalogPicker;
    @FXML private Label messageLabel;
    @FXML private Button addButton,removeButton;
    @FXML public void initialize(){catalogPicker.setItems(FXCollections.observableArrayList(AppServices.marketplace().coffeeProducts()));boolean readOnly=AppServices.isReadOnlyWorkspacePreview();addButton.setDisable(readOnly);removeButton.setDisable(readOnly);refresh();}
    private void refresh(){productList.setItems(FXCollections.observableArrayList(AppServices.marketplace().supplierProducts()));productList.setPlaceholder(new Label("No products selected yet."));}
    @FXML private void addProduct(){try{String name=catalogPicker.getValue();if(name==null)throw new IllegalArgumentException("Select an existing coffee product first.");boolean added=AppServices.marketplace().addSupplierProduct(name);messageLabel.setText(added?"Product added to this catalog.":"That product is already in the catalog.");if(added)UndoHistory.register(screenRoot,"addition of "+name+" to supplier products",()->AppServices.marketplace().removeSupplierProduct(name),messageLabel::setText);catalogPicker.setValue(null);refresh();}catch(IllegalArgumentException e){messageLabel.setText(e.getMessage());}}
    @FXML private void removeProduct(){String selected=productList.getSelectionModel().getSelectedItem();if(selected==null){messageLabel.setText("Select a listed product first.");return;}var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Remove "+selected+" from your demo catalog?",ButtonType.CANCEL,ButtonType.OK);confirm.setHeaderText("Remove supplier product");if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;AppServices.marketplace().removeSupplierProduct(selected);UndoHistory.register(screenRoot,"removal of "+selected+" from supplier products",()->AppServices.marketplace().addSupplierProduct(selected),messageLabel::setText);messageLabel.setText(selected+" removed. Press ⌘Z (macOS) or Ctrl+Z to restore it.");refresh();}
    @FXML private void openDashboard(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");}
    @FXML private void goBack(){SceneNavigator.back(screenRoot,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Coffee Mongers · Supplier Dashboard");}
    @FXML private void openWeeklySupply(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/WeeklySupply.fxml","Coffee Mongers · Weekly Supply");}
    @FXML private void openOrders(){SceneNavigator.show(screenRoot,"/com/coffeemongers/supplier/SupplierOrders.fxml","Coffee Mongers · Supplier Orders");}
}
