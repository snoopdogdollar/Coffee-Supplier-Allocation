package com.coffeemongers.controller;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerDemandService.Item;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.BuyerDemandRow;
import com.coffeemongers.view.DatabaseMessages;
import com.coffeeallocation.model.BuyerWorkspaceView;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;

public class DemandDetailsController {
    private static final String DASHBOARD = "/com/coffeemongers/view/BuyerDashboard.fxml";
    private static final String DASHBOARD_TITLE = "Coffee Mongers · Buyer Dashboard";

    @FXML private BorderPane detailsRoot;
    @FXML private Label demandIdLabel, demandTitleLabel, totalQuantityLabel, deliveryDateLabel, notesLabel, demandStatusLabel;
    @FXML private Label requiredWeekLabel, requiredWeekValue, submissionMessage, shopNameLabel, shopCityLabel;
    @FXML private Button demandActionButton,refreshDemandButton;
    @FXML private BuyerSidebarController buyerSidebarController;
    @FXML private VBox allocationSection,allocationRows;
    @FXML private VBox detailsProductContainer;
    private DemandRequest demand;
    private LocalDate requiredDeliveryDate;
    private List<Item> liveItems = List.of();
    private boolean savedDemand;
    private boolean refreshDashboardOnExit;
    private boolean submitting;
    @FXML public void initialize(){buyerSidebarController.setActive("demands");}

    public void setDemand(DemandRequest demand) {
        this.demand = demand;
        renderDemand();
        boolean draft = isDraft();
        demandStatusLabel.setText(draft ? "DRAFT" : "SUBMITTED · DEMO");
        savedDemand=!draft;
        showLiveDetails(demand.requiredDeliveryDate().get(IsoFields.WEEK_BASED_YEAR),demand.requiredDeliveryDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        shopNameLabel.setText(AppServices.currentUser().map(user -> user.fullName()).orElse("Your coffee shop"));
        shopCityLabel.setText(demand.shopAddress().isBlank()?"—":demand.shopAddress());
        demandActionButton.setText(draft ? "Submit Demand" : "Back to My Demands");
        demandActionButton.setDisable(draft&&AppServices.isReadOnlyWorkspacePreview());
        submissionMessage.setText(draft?"Review your request before submitting.":"Submitted in this demo session. Allocation is managed by Admin in the connected workspace.");
    }

    public void setLiveDraft(DemandRequest draft, LocalDate requiredDate, List<Item> items) {
        this.demand = draft;
        requiredDeliveryDate = requiredDate;
        liveItems = List.copyOf(items);
        renderDemand();
        showLiveDetails(requiredDate.get(IsoFields.WEEK_BASED_YEAR),
                requiredDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        deliveryDateLabel.setText("Set when submitted");
        demandStatusLabel.setText("DRAFT");
        savedDemand=false;
        demandActionButton.setText("Submit Demand");
    }

    public void setSavedDemand(DemandRequest demand, String status, int requiredYear, int requiredWeek) {
        this.demand = demand;
        savedDemand = true;
        requiredDeliveryDate = null;
        liveItems = List.of();
        renderDemand();
        showLiveDetails(requiredYear, requiredWeek);
        demandStatusLabel.setText(status);
        demandActionButton.setText("View My Orders");
        showSavedDemand(Long.parseLong(demand.id()));
    }

    public void showSavedDemand(long demandId) {
        var service=AppServices.liveBuyerDemands();var user=AppServices.currentUser();
        if(service.isEmpty()){submissionMessage.setText("Sign in as your linked Buyer to view this demand.");return;}
        savedDemand=true;demandActionButton.setDisable(true);refreshDemandButton.setDisable(true);
        demandIdLabel.setText("Demand #"+demandId);demandTitleLabel.setText("Loading demand…");demandStatusLabel.setText("—");
        deliveryDateLabel.setText("—");requiredWeekValue.setText("—");notesLabel.setText("—");totalQuantityLabel.setText("—");
        detailsProductContainer.getChildren().clear();allocationRows.getChildren().clear();
        allocationSection.setVisible(false);allocationSection.setManaged(false);
        submissionMessage.setText("Loading demand and confirmed allocation…");
        Task<BuyerWorkspaceView.Detail> task=new Task<>(){@Override protected BuyerWorkspaceView.Detail call()throws Exception{return service.get().workspace().detail(demandId);}};
        task.setOnSucceeded(event -> {
            if(!AppServices.currentUser().equals(user)||AppServices.liveBuyerDemands().isEmpty())return;
            var detail=task.getValue();demand=BuyerDemandRow.of(detail.demand()).request();renderDemand();
            showLiveDetails(detail.demand().year(),detail.demand().week());demandStatusLabel.setText(detail.demand().status());
            shopNameLabel.setText(detail.shop().name());shopCityLabel.setText(detail.shop().address()==null?"—":detail.shop().address());
            detailsProductContainer.getChildren().clear();
            for(var item:detail.demand().items()) {
                Label name=new Label(item.productName());name.getStyleClass().add("supplier-name");name.setWrapText(true);
                VBox info=new VBox(4,name);HBox.setHgrow(info,Priority.ALWAYS);
                if(item.note()!=null&&!item.note().isBlank()){Label note=new Label(item.note());note.getStyleClass().add("small-text");info.getChildren().add(note);}
                HBox row=new HBox(12,info,amount(item.requested(),item.unit()),amount(item.allocated(),item.unit()),amount(item.remaining(),item.unit()));
                row.setAlignment(Pos.CENTER_LEFT);detailsProductContainer.getChildren().add(row);
            }
            allocationSection.setVisible(true);allocationSection.setManaged(true);
            if(detail.allocations().isEmpty()){Label empty=new Label("No confirmed allocation yet. Admin will review available weekly supply.");empty.setWrapText(true);empty.getStyleClass().add("small-text");allocationRows.getChildren().add(empty);}
            for(var allocation:detail.allocations()) {
                Label supplier=new Label(allocation.supplier()+" (#"+allocation.supplierId()+")");supplier.getStyleClass().add("supplier-name");supplier.setWrapText(true);
                Label product=new Label(allocation.product()+" · allocation #"+allocation.id());product.getStyleClass().add("small-text");
                VBox info=new VBox(4,supplier,product);HBox.setHgrow(info,Priority.ALWAYS);
                Label quantity=amount(allocation.quantity(),allocation.unit());
                Label price=new Label(allocation.unitPrice().toPlainString()+" VND/"+allocation.unit());price.getStyleClass().add("small-text");
                HBox row=new HBox(12,info,new VBox(4,quantity,price));row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("supplier-row");allocationRows.getChildren().add(row);
            }
            demandActionButton.setText("View My Orders");demandActionButton.setDisable(false);
            refreshDemandButton.setVisible(true);refreshDemandButton.setManaged(true);refreshDemandButton.setDisable(false);
            submissionMessage.setText("Requested, confirmed allocation and remaining quantities are shown separately for each product.");
        });
        task.setOnFailed(event -> {if(!AppServices.currentUser().equals(user))return;refreshDemandButton.setDisable(false);
            refreshDemandButton.setVisible(true);refreshDemandButton.setManaged(true);
            refreshDemandButton.setUserData(demandId);submissionMessage.setText(DatabaseMessages.describe(task.getException(),"Could not load this demand. Refresh to try again."));});
        refreshDemandButton.setUserData(demandId);Thread.ofVirtual().name("buyer-demand-detail").start(task);
    }
    private Label amount(java.math.BigDecimal value,String unit){Label label=new Label(value.stripTrailingZeros().toPlainString()+" "+unit);label.setMinWidth(115);label.setPrefWidth(115);label.setAlignment(Pos.CENTER_RIGHT);label.getStyleClass().add("buyer-quantity");return label;}
    @FXML private void refreshDemand(){if(refreshDemandButton.getUserData() instanceof Long id)showSavedDemand(id);}

    private void showLiveDetails(int year, int week) {
        shopNameLabel.setText("Your linked coffee shop");
        shopCityLabel.setText("—");
        requiredWeekLabel.setVisible(true);
        requiredWeekLabel.setManaged(true);
        requiredWeekValue.setVisible(true);
        requiredWeekValue.setManaged(true);
        requiredWeekValue.setText(String.format(Locale.ROOT, "%04d-W%02d", year, week));
    }

    private void renderDemand() {
        demandIdLabel.setText(isDraft() ? "Draft demand · not yet submitted" : demand.id());
        demandTitleLabel.setText(demand.title());
        deliveryDateLabel.setText(demand.requestDate().toString());
        notesLabel.setText(demand.notes().isBlank() ? "No additional notes" : demand.notes());
        detailsProductContainer.getChildren().clear();
        for (DemandItem item : demand.items()) {
            Label product = new Label(item.productName());
            product.getStyleClass().add("supplier-name");
            VBox productInfo = new VBox(3, product);
            if (!item.note().isBlank()) {
                Label note = new Label(item.note());
                note.getStyleClass().add("small-text");
                productInfo.getChildren().add(note);
            }
            Label quantity = new Label(item.quantity().stripTrailingZeros().toPlainString() + " " + item.unit());
            quantity.getStyleClass().add("score");
            quantity.setMinWidth(115);quantity.setPrefWidth(115);
            quantity.setAlignment(Pos.CENTER_RIGHT);
            Label allocated=new Label("—"),remaining=new Label("—");
            for(var label:List.of(allocated,remaining)){label.setMinWidth(115);label.setPrefWidth(115);label.setAlignment(Pos.CENTER_RIGHT);}
            HBox row = new HBox(12, productInfo, quantity,allocated,remaining);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(productInfo, Priority.ALWAYS);
            detailsProductContainer.getChildren().add(row);
        }
        totalQuantityLabel.setText(demand.items().size() + " product line(s)");
    }

    private boolean isDraft() {
        return demand.id() == null || demand.id().isBlank();
    }

    @FXML
    private void demandAction() {
        if (savedDemand) {
            if(AppServices.liveBuyerDemands().isPresent())SceneNavigator.show(detailsRoot,"/com/coffeemongers/view/MyOrders.fxml","Coffee Mongers · My Orders");
            else SceneNavigator.clearAndShow(detailsRoot,"/com/coffeemongers/view/MyDemands.fxml","Coffee Mongers · My Demands");
        } else if (requiredDeliveryDate != null) {
            submitLiveDemand();
        } else {
            submitDemoDemand();
        }
    }

    private void submitLiveDemand() {
        if (submitting) {
            return;
        }
        var service = AppServices.liveBuyerDemands();
        var currentBuyer = AppServices.currentUser();
        if (service.isEmpty() || currentBuyer.isEmpty()) {
            submissionMessage.setText("Sign in as the configured test buyer to submit this demand.");
            return;
        }

        LocalDate requiredDate = requiredDeliveryDate;
        DemandRequest draft = demand;
        List<Item> items = liveItems;
        submitting = true;
        detailsRoot.setDisable(true);
        submissionMessage.setText("Saving demand…");

        Task<Long> task = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return service.orElseThrow().submit(requiredDate, draft.notes(), items);
            }
        };
        task.setOnSucceeded(event -> {
            finishSubmission();
            if (!AppServices.currentUser().equals(currentBuyer)) {
                return;
            }
            refreshDashboardOnExit = true;
            DemandRequest saved = new DemandRequest(Long.toString(task.getValue()), draft.title(),
                    LocalDate.now(), draft.shopAddress(), draft.notes(), draft.items(),requiredDate);
            setSavedDemand(saved, "OPEN", requiredDate.get(IsoFields.WEEK_BASED_YEAR),
                    requiredDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        });
        task.setOnFailed(event -> {
            finishSubmission();
            if (!AppServices.currentUser().equals(currentBuyer)) {
                return;
            }
            Throwable failure = task.getException();
            submissionMessage.setText(failure instanceof IllegalArgumentException
                    ? failure.getMessage()
                    : "Could not confirm the save. Check your Buyer Dashboard before trying again.");
        });
        Thread worker = new Thread(task, "buyer-demand-submit");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishSubmission() {
        submitting = false;
        detailsRoot.setDisable(false);
    }

    private void submitDemoDemand() {
        try {
            if (isDraft()) {
                demand = AppServices.marketplace().submitDemand(demand);
                demandIdLabel.setText(demand.id());
                demandStatusLabel.setText("SUBMITTED · DEMO");
            }
            refreshDashboardOnExit=true;
            setDemand(demand);
        } catch (RuntimeException ex) {
            submissionMessage.setText("Could not submit this demand: " + ex.getMessage());
        }
    }

    @FXML
    private void goBack() {
        if (refreshDashboardOnExit) {
            SceneNavigator.clearAndShow(detailsProductContainer, DASHBOARD, DASHBOARD_TITLE);
        } else {
            SceneNavigator.back(detailsProductContainer, DASHBOARD, DASHBOARD_TITLE);
        }
    }
}
