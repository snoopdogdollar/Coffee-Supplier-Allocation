package com.coffeemongers.controller;

import com.coffeeallocation.model.SupplyComposition.*;
import com.coffeemongers.service.*;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.view.DatabaseMessages;
import javafx.animation.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.util.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;

/** Dynamic product categories and supplier stacks, refreshed only on the visible Admin dashboard. */
public class SupplyCompositionController {
    public static final int REFRESH_SECONDS=5;
    @FXML private VBox compositionRoot,compositionBody;
    @FXML private FlowPane compositionLegend;
    @FXML private DatePicker compositionWeek;
    @FXML private ComboBox<Measure> compositionMeasure;
    @FXML private Button compositionRefreshButton;
    @FXML private Label compositionScope,compositionStatus;
    private final SupplyCompositionService service=new SupplyCompositionService();
    private Snapshot snapshot;
    private boolean configured,live,busy,queued;
    private Optional<DemoUser> owner=Optional.empty();
    private Timeline timer;
    private Window observedWindow;
    private final ChangeListener<Window> sceneWindowListener=(property,previous,window) -> watchWindow(window);
    private final ChangeListener<Boolean> showingListener=(o,old,showing) -> updateTimer();
    @FXML public void initialize(){
        compositionMeasure.getItems().setAll(Measure.values());compositionMeasure.setValue(Measure.REMAINING);
        compositionMeasure.valueProperty().addListener((o,old,value) -> {if(snapshot!=null)render();});
        timer=new Timeline(new KeyFrame(Duration.seconds(REFRESH_SECONDS),event -> {
            if(!visibleOwner()){timer.stop();return;}if(!busy)read(false);
        }));timer.setCycleCount(Timeline.INDEFINITE);
        compositionRoot.sceneProperty().addListener((o,old,scene) -> {
            if(old!=null)old.windowProperty().removeListener(sceneWindowListener);
            if(scene==null){watchWindow(null);return;}
            scene.windowProperty().addListener(sceneWindowListener);watchWindow(scene.getWindow());});
    }
    public void configureLive(){configured=true;live=true;owner=AppServices.currentUser();updateTimer();}
    public void configureDemo(){configured=true;live=false;owner=AppServices.currentUser();read(true);updateTimer();}
    @FXML public void reload(){read(true);}
    private void watchWindow(Window window){if(observedWindow!=null)observedWindow.showingProperty().removeListener(showingListener);observedWindow=window;if(window!=null)window.showingProperty().addListener(showingListener);updateTimer();}
    private boolean visibleOwner(){return configured&&owner.equals(AppServices.currentUser())&&compositionRoot.getScene()!=null&&compositionRoot.getScene().getWindow()!=null&&compositionRoot.getScene().getWindow().isShowing()&&compositionRoot.getScene().getWindow().getScene()==compositionRoot.getScene();}
    private void updateTimer(){if(timer==null)return;if(visibleOwner())timer.play();else timer.stop();}
    private void read(boolean manual){
        if(!configured||!owner.equals(AppServices.currentUser()))return;
        if(busy){if(manual)queued=true;return;}
        if(live&&AppServices.liveAdminOverview().isEmpty()){timer.stop();return;}
        LocalDate requested=manual||snapshot==null?compositionWeek.getValue():snapshot.week();
        if(!live){snapshot=service.demo(AppServices.marketplace(),requested);if(manual||compositionWeek.getValue()==null)compositionWeek.setValue(snapshot.week());render();return;}
        busy=true;if(manual||snapshot==null){compositionRefreshButton.setDisable(true);compositionWeek.setDisable(true);}
        if(snapshot==null)compositionStatus.setText("Loading supply contributions…");
        var user=owner;
        Task<Snapshot> task=new Task<>(){@Override protected Snapshot call()throws Exception{return service.load(requested);}};
        task.setOnSucceeded(event -> {finish();if(!user.equals(AppServices.currentUser())||!live||AppServices.liveAdminOverview().isEmpty())return;
            var fresh=task.getValue();boolean changed=snapshot==null||!snapshot.week().equals(fresh.week())||!snapshot.products().equals(fresh.products())||!snapshot.contributions().equals(fresh.contributions());
            snapshot=fresh;if(manual||compositionWeek.getValue()==null)compositionWeek.setValue(snapshot.week());if(changed)render();else showFreshness();readQueued();});
        task.setOnFailed(event -> {finish();if(!user.equals(AppServices.currentUser())||AppServices.liveAdminOverview().isEmpty())return;
            compositionStatus.setText(DatabaseMessages.describe(task.getException(),"Could not update supply contributions.")+" Retrying while this dashboard is open."
                    +(snapshot==null?"":" Last successful update: "+snapshot.loadedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"."));readQueued();});
        Thread.ofVirtual().name("admin-supply-composition").start(task);
    }
    private void finish(){busy=false;compositionRefreshButton.setDisable(false);compositionWeek.setDisable(false);}
    private void readQueued(){if(queued){queued=false;if(visibleOwner())read(true);}}
    private void render(){
        compositionBody.getChildren().clear();compositionLegend.getChildren().clear();
        Measure measure=compositionMeasure.getValue()==null?Measure.REMAINING:compositionMeasure.getValue();
        compositionScope.setText("%04d-W%02d · %s · %d active coffee types".formatted(snapshot.week().get(IsoFields.WEEK_BASED_YEAR),snapshot.week().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),measure,snapshot.products().size()));
        Map<Long,String> suppliers=new LinkedHashMap<>();snapshot.contributions().stream().filter(c -> c.amount(measure).signum()>0).forEach(c -> suppliers.putIfAbsent(c.supplierId(),c.supplierName()));
        suppliers.forEach((id,name) -> {Region swatch=new Region();swatch.setMinSize(12,12);swatch.setPrefSize(12,12);swatch.setMaxSize(12,12);swatch.setStyle("-fx-background-color:"+color(id)+";-fx-background-radius:3;");Label label=new Label(name+" (#"+id+")");label.getStyleClass().add("small-text");HBox item=new HBox(6,swatch,label);item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);compositionLegend.getChildren().add(item);});
        if(snapshot.products().isEmpty()){Label empty=new Label("No active coffee types yet. Add a coffee product to create its column.");empty.setWrapText(true);empty.getStyleClass().add("empty-state");compositionBody.getChildren().add(empty);}
        for(String unit:snapshot.units()){
            var products=snapshot.products().stream().filter(p -> p.unit().equals(unit)).toList();
            boolean hasSupply=products.stream().anyMatch(p -> snapshot.total(p.id(),measure).signum()>0);
            CategoryAxis x=new CategoryAxis();x.setLabel("Coffee type");x.setAutoRanging(false);x.setCategories(FXCollections.observableArrayList(products.stream().map(this::category).toList()));
            NumberAxis y=new NumberAxis();y.setLabel("Quantity ("+unit+")");y.setForceZeroInRange(true);
            if(!hasSupply){y.setAutoRanging(false);y.setLowerBound(0);y.setUpperBound(1);y.setTickUnit(1);}
            StackedBarChart<String,Number> chart=new StackedBarChart<>(x,y);chart.setAnimated(false);chart.setLegendVisible(false);chart.setCategoryGap(24);chart.setVerticalGridLinesVisible(false);chart.setPrefHeight(hasSupply?320:160);chart.setMinHeight(hasSupply?300:140);chart.setMinWidth(Math.max(480,products.size()*125));chart.getStyleClass().add("supply-composition-chart");
            suppliers.forEach((id,name) -> {
                boolean contributes=products.stream().anyMatch(p -> snapshot.amount(p.id(),id,measure).signum()>0);if(!contributes)return;
                var series=new XYChart.Series<String,Number>();series.setName(name+" (#"+id+")");
                for(var product:products){var amount=snapshot.amount(product.id(),id,measure);var data=new XYChart.Data<String,Number>(category(product),amount.doubleValue());
                    String tooltip=product.name()+"\n"+name+": "+amount.stripTrailingZeros().toPlainString()+" "+unit+"\nContribution: "+snapshot.percentage(product.id(),id,measure).toPlainString()+"% of "+snapshot.total(product.id(),measure).stripTrailingZeros().toPlainString()+" "+unit;
                    data.nodeProperty().addListener((o,old,node) -> {if(node!=null){node.setStyle("-fx-bar-fill:"+color(id)+";-fx-background-color:"+color(id)+";");Tooltip.install(node,new Tooltip(tooltip));}});series.getData().add(data);}
                chart.getData().add(series);
            });
            ScrollPane scroller=new ScrollPane(chart);scroller.setFitToWidth(true);scroller.setFitToHeight(true);scroller.setPrefHeight(hasSupply?345:185);scroller.setMinWidth(0);scroller.getStyleClass().add("composition-scroll");
            Label heading=new Label("Supply in "+unit+(hasSupply?"":" · no supply for the selected measure/week"));heading.getStyleClass().add("supplier-name");FlowPane totals=new FlowPane(12,8);
            for(var product:products){Label total=new Label(product.name()+": "+snapshot.total(product.id(),measure).stripTrailingZeros().toPlainString()+" "+unit);total.setId("composition-total-"+product.id());total.getStyleClass().add("composition-total");Tooltip.install(total,new Tooltip("Zero means no "+measure.toString().toLowerCase(Locale.ROOT)+" in the selected week."));totals.getChildren().add(total);}
            compositionBody.getChildren().add(new VBox(8,heading,scroller,totals));
        }
        showFreshness();
    }
    private void showFreshness(){compositionStatus.setText((live?"Auto-refresh every "+REFRESH_SECONDS+" seconds while this dashboard is open. ":"Demo session data · ")
                +"Last updated "+snapshot.loadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+". Only active coffee types, Suppliers and offerings contribute. Quantities are grouped by unit.");
    }
    private String category(Product product){return snapshot.products().stream().filter(p -> p.name().equals(product.name())).count()>1?product.name()+" (#"+product.id()+")":product.name();}
    private static String color(long supplierId){Color color=Color.hsb((supplierId*137.508)%360,0.46,0.68);return "#%02x%02x%02x".formatted(Math.round(color.getRed()*255),Math.round(color.getGreen()*255),Math.round(color.getBlue()*255));}
}
