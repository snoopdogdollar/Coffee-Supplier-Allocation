package com.coffeemongers.controller;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerDemandService;
import com.coffeemongers.service.BuyerDemandService.Item;
import com.coffeemongers.service.BuyerDemandService.Product;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.FormFeedback;
import com.coffeemongers.view.ViewFeedback;
import com.coffeemongers.view.DatabaseMessages;
import static com.coffeemongers.view.ViewFeedback.Kind.*;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateDemandController {
    @FXML private BuyerSidebarController buyerSidebarController;
    @FXML private DatePicker requestDatePicker;
    @FXML private TextArea notesField;
    @FXML private VBox productRowsContainer;
    @FXML private Label productErrorLabel, catalogStatusLabel, previewNotice, shopLabel, dateLabel,requiredWeekPreview,dateErrorLabel,notesErrorLabel;
    @FXML private Button reviewDemandButton, addProductButton,retryCatalogButton;
    private final List<ProductRow> productRows = new ArrayList<>();
    private final Map<String, Product> products = new LinkedHashMap<>();
    private boolean liveBuyer;
    private boolean catalogLoading,reviewOpening;
    private int rowSequence;
    private final FormFeedback feedback=new FormFeedback();

    @FXML
    public void initialize() {
        buyerSidebarController.setActive("create");
        ViewFeedback.show(productErrorLabel,INFO,"");
        dateLabel.setText("Required Delivery Date *");
        requestDatePicker.setValue(LocalDate.now());
        feedback.register(requestDatePicker,dateErrorLabel,requestDatePicker.valueProperty(),requestDatePicker.getEditor().textProperty());
        feedback.register(notesField,notesErrorLabel,notesField.textProperty());
        updateWeek();requestDatePicker.valueProperty().addListener((o,old,date) -> updateWeek());
        boolean readOnly = AppServices.isReadOnlyWorkspacePreview();
        previewNotice.setVisible(readOnly);
        previewNotice.setManaged(readOnly);
        reviewDemandButton.setDisable(readOnly);
        if (readOnly) {
            reviewDemandButton.setTooltip(new Tooltip("Sign in with a Buyer account to submit this demand. Admin workspace previews are read-only."));
        }

        var service = AppServices.liveBuyerDemands();
        liveBuyer = service.isPresent();
        if (liveBuyer) {
            shopLabel.setText("Your linked coffee shop");
            dateLabel.setText("Required Delivery Date *");
            requestDatePicker.setPromptText("Select a date in the week you need the coffee");
            loadCatalog(service.orElseThrow());
        } else {
            var marketplace = AppServices.marketplace();
            for (String name : marketplace.coffeeProducts()) {
                products.put(name, new Product(0, name, marketplace.coffeeProductUnit(name)));
            }
            addProductRow();
            if(products.isEmpty()){showCatalogStatus("No coffee products are available. Ask Admin to add a product.",INFO);reviewDemandButton.setDisable(true);addProductButton.setDisable(true);}
        }
    }

    private void loadCatalog(BuyerDemandService service) {
        if(catalogLoading)return;
        var user=AppServices.currentUser();catalogLoading=true;products.clear();
        addProductButton.setDisable(true);
        reviewDemandButton.setDisable(true);
        reviewDemandButton.setText("Loading products…");
        retryCatalogButton.setVisible(false);retryCatalogButton.setManaged(false);
        showCatalogStatus("Loading available coffee products…",LOADING);
        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return service.products();
            }
        };
        task.setOnSucceeded(event -> {
            catalogLoading=false;reviewDemandButton.setText("Review demand →");
            if(!AppServices.currentUser().equals(user))return;
            for (Product product : task.getValue()) {
                products.put(product.name() + " · #" + product.id(), product);
            }
            if (products.isEmpty()) {
                showCatalogStatus("No active coffee products are available. Ask Admin to add a product, then retry.",INFO);
                retryCatalogButton.setVisible(true);retryCatalogButton.setManaged(true);
                return;
            }
            showCatalogStatus("Products loaded. Enter the quantity you need for each product.",INFO);
            addProductRow();
            addProductButton.setDisable(false);
            reviewDemandButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            catalogLoading=false;reviewDemandButton.setText("Review demand →");
            if(!AppServices.currentUser().equals(user))return;
            showCatalogStatus(DatabaseMessages.describe(task.getException(),"Could not load products. Retry when the connection is available."),ERROR);
            retryCatalogButton.setVisible(true);retryCatalogButton.setManaged(true);
        });
        Thread worker = new Thread(task, "buyer-demand-catalog");
        worker.setDaemon(true);
        worker.start();
    }

    private void showCatalogStatus(String message,ViewFeedback.Kind kind) {ViewFeedback.show(catalogStatusLabel,kind,message);}
    @FXML private void retryCatalog(){AppServices.liveBuyerDemands().ifPresent(this::loadCatalog);}

    private void updateWeek(){var date=requestDatePicker.getValue();requiredWeekPreview.setText(date==null?"Choose a delivery date.":"Delivery week: %04d-W%02d · Admin allocates weekly supply for this week.".formatted(date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR),date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)));}

    @FXML
    private void addProductRow() {
        ComboBox<String> product = new ComboBox<>(FXCollections.observableArrayList(products.keySet()));
        product.setPromptText("Select coffee product");
        product.setMaxWidth(Double.MAX_VALUE);
        Label unit = new Label("—");
        unit.getStyleClass().add("small-text");
        product.valueProperty().addListener((obs, old, selected) -> unit.setText(selected == null ? "—" : products.get(selected).unit()));
        TextField quantity = new TextField();
        quantity.setPromptText("Quantity");
        quantity.setMaxWidth(Double.MAX_VALUE);
        TextField note = new TextField();
        note.setPromptText("Product note (optional)");
        note.setMaxWidth(Double.MAX_VALUE);
        Button remove = new Button("Remove");
        remove.getStyleClass().add("remove-button");

        GridPane grid = new GridPane();
        grid.setHgap(10);grid.setVgap(6);
        grid.setAlignment(Pos.CENTER_LEFT);
        ColumnConstraints flexible = new ColumnConstraints();
        flexible.setHgrow(Priority.ALWAYS);
        flexible.setMinWidth(0);
        grid.getColumnConstraints().setAll(flexible, new ColumnConstraints(100, 100, 100),
                new ColumnConstraints(55, 55, 55), new ColumnConstraints(160, 160, 160),
                new ColumnConstraints(78, 78, 78));
        grid.add(product, 0, 0);
        grid.add(quantity, 1, 0);
        grid.add(unit, 2, 0);
        grid.add(note, 3, 0);
        grid.add(remove, 4, 0);
        Label productError=new Label(),quantityError=new Label(),noteError=new Label();
        grid.add(productError,0,1);grid.add(quantityError,1,1);grid.add(noteError,3,1);
        feedback.register(product,productError,product.valueProperty());
        feedback.register(quantity,quantityError,quantity.textProperty());
        feedback.register(note,noteError,note.textProperty());
        int number=++rowSequence;product.setAccessibleText("Product in row "+number);quantity.setAccessibleText("Quantity in row "+number);note.setAccessibleText("Product note in row "+number);
        quantity.setId("demandQuantity-"+number);product.setId("demandProduct-"+number);note.setId("demandNote-"+number);

        ProductRow row = new ProductRow(product, quantity, note);
        productRows.add(row);
        productRowsContainer.getChildren().add(grid);
        remove.setOnAction(e -> {
            if (productRows.size() == 1) {
                showError("At least one coffee product is required.");
                return;
            }
            productRows.remove(row);
            feedback.remove(product,quantity,note);
            productRowsContainer.getChildren().remove(grid);
            ViewFeedback.show(productErrorLabel,INFO,"");
        });
        ViewFeedback.show(productErrorLabel,INFO,"");
    }

    @FXML
    private void submitDemand() {
        if(catalogLoading||reviewOpening||AppServices.isReadOnlyWorkspacePreview())return;
        feedback.clearAll();
        ViewFeedback.show(productErrorLabel,INFO,"");
        LocalDate selectedDate=null;
        try{selectedDate=FormFeedback.date(requestDatePicker);if(selectedDate.isBefore(LocalDate.now()))feedback.error(requestDatePicker,"Choose today or a later date.");}
        catch(IllegalArgumentException error){feedback.error(requestDatePicker,error.getMessage());}
        String notes=notesField.getText().trim();

        List<DemandItem> items = new ArrayList<>();
        List<Item> liveItems = new ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for (int i = 0; i < productRows.size(); i++) {
            ProductRow row = productRows.get(i);
            Product selected = products.get(row.product().getValue());
            if (selected == null) {
                feedback.error(row.product(),"Choose a coffee product.");
            }else if(!seen.add(row.product().getValue())){
                feedback.error(row.product(),"This product is already listed. Combine its quantity in one row.");
            }
            String note = row.note().getText().trim();
            if(note.codePointCount(0,note.length())>255)feedback.error(row.note(),"Keep the product note within 255 characters.");
            try {
                BigDecimal quantity = FormFeedback.amount(row.quantity().getText(),"Quantity",false);
                if(selected==null)continue;
                items.add(new DemandItem(selected.name(), quantity, selected.unit(), note));
                if (liveBuyer) {
                    liveItems.add(new Item(selected.id(), quantity, note));
                }
            } catch (IllegalArgumentException ex) {
                feedback.error(row.quantity(),ex.getMessage());
            }
        }
        if(notes.codePointCount(0,notes.length())>255)feedback.error(notesField,"Keep additional notes within 255 characters.");
        if(!feedback.valid()){showError("Check the highlighted fields before continuing.");feedback.focusFirst();return;}
        if(items.isEmpty()){showError("Add at least one coffee product.");return;}

        String title = items.stream().map(DemandItem::productName).distinct().collect(Collectors.joining(", "));
        DemandRequest draft = new DemandRequest("", title, LocalDate.now(),
                liveBuyer ? "" : "Demo coffee shop", notes, items,selectedDate);
        LocalDate deliveryDate=selectedDate;
        reviewOpening=true;reviewDemandButton.setDisable(true);
        try {
            SceneNavigator.show(productRowsContainer, "/com/coffeemongers/createdemand/DemandDetails.fxml",
                    "Coffee Mongers · Demand Details", controller -> {
                        var details = (DemandDetailsController) controller;
                        if (liveBuyer) {
                            details.setLiveDraft(draft, deliveryDate, liveItems);
                        } else {
                            details.setDemand(draft);
                        }
                    });
        } catch (IllegalStateException ex) {
            showError("Could not open the demand review.");
        }finally{reviewOpening=false;reviewDemandButton.setDisable(AppServices.isReadOnlyWorkspacePreview());}
    }

    private void showError(String message) {
        ViewFeedback.show(productErrorLabel,ERROR,message);
    }

    @FXML
    private void goBack() {
        SceneNavigator.back(productRowsContainer, "/com/coffeemongers/view/BuyerDashboard.fxml",
                "Coffee Mongers · Buyer Dashboard");
    }

    private record ProductRow(ComboBox<String> product, TextField quantity, TextField note) {}
}
