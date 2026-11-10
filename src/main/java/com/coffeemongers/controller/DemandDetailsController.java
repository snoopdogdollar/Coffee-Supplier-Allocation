package com.coffeemongers.controller;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.service.BuyerDemandService.Item;
import com.coffeemongers.view.SceneNavigator;
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
    @FXML private Button findMatchesButton;
    @FXML private VBox detailsProductContainer;
    private DemandRequest demand;
    private LocalDate requiredDeliveryDate;
    private List<Item> liveItems = List.of();
    private boolean savedDemand;
    private boolean refreshDashboardOnExit;
    private boolean submitting;

    public void setDemand(DemandRequest demand) {
        this.demand = demand;
        renderDemand();
        boolean draft = isDraft();
        demandStatusLabel.setText(draft ? "DRAFT" : "SUBMITTED · DEMO");
        findMatchesButton.setText(draft ? "Submit Demand & Find Matches" : "View Supplier Matches");
        findMatchesButton.setDisable(AppServices.isReadOnlyWorkspacePreview());
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
        findMatchesButton.setText("Submit Demand");
    }

    public void setSavedDemand(DemandRequest demand, String status, int requiredYear, int requiredWeek) {
        this.demand = demand;
        savedDemand = true;
        requiredDeliveryDate = null;
        liveItems = List.of();
        renderDemand();
        showLiveDetails(requiredYear, requiredWeek);
        demandStatusLabel.setText(status);
        findMatchesButton.setText("Back to Dashboard");
    }

    private void showLiveDetails(int year, int week) {
        shopNameLabel.setText("Test shop linked to your buyer account");
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
            quantity.setMinWidth(140);
            quantity.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(12, productInfo, quantity);
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
    private void findMatches() {
        if (savedDemand) {
            goBack();
        } else if (requiredDeliveryDate != null) {
            submitLiveDemand();
        } else {
            openDemoMatches();
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
                    LocalDate.now(), draft.shopAddress(), draft.notes(), draft.items());
            setSavedDemand(saved, "OPEN", requiredDate.get(IsoFields.WEEK_BASED_YEAR),
                    requiredDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            submissionMessage.setText("");
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

    private void openDemoMatches() {
        try {
            if (isDraft()) {
                demand = AppServices.marketplace().submitDemand(demand);
                demandIdLabel.setText(demand.id());
                demandStatusLabel.setText("SUBMITTED · DEMO");
            }
            SceneNavigator.show(detailsProductContainer, "/com/coffeemongers/createdemand/MatchingResults.fxml",
                    "Coffee Mongers · Matching Results",
                    controller -> ((MatchingResultsController) controller).setDemand(demand));
        } catch (RuntimeException ex) {
            notesLabel.setText("Could not load recommendations: " + ex.getMessage());
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
