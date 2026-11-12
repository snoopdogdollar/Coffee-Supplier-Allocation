package com.coffeemongers.controller;

import com.coffeemongers.model.schema.CoffeeProduct;
import com.coffeemongers.model.schema.CoffeeShop;
import com.coffeemongers.model.schema.Supplier;
import com.coffeemongers.service.AppServices;
import com.coffeemongers.view.SceneNavigator;
import com.coffeemongers.view.UndoHistory;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

public class AdminManagementController {
    @FXML private BorderPane screenRoot;
    @FXML private TabPane managementTabs;
    private final Map<String, TableView<AdminRow>> tables = new LinkedHashMap<>();
    private final Map<String, FilteredList<AdminRow>> filtered = new HashMap<>();
    private final Map<String, javafx.collections.ObservableList<AdminRow>> sources = new HashMap<>();
    private ComboBox<String> supplierProductPicker;
    public void selectSection(String title){for(Tab tab:managementTabs.getTabs())if(tab.getText().equals(title)){managementTabs.getSelectionModel().select(tab);break;}}

    @FXML public void initialize() {
        managementTabs.getTabs().setAll(catalogTab("Coffee Shops"),catalogTab("Suppliers"),catalogTab("Coffee Products"),supplierProductsTab(),supplyTab(),activityTab(),reviewsTab());
        refresh();
    }
    private Tab catalogTab(String section) {
        TableView<AdminRow> table = new TableView<>(); table.getStyleClass().add("data-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); tables.put(section,table);
        TableColumn<AdminRow,String> id=column("ID",AdminRow::id,80),name=column("Name",AdminRow::name,190),detail=column("Details",AdminRow::detail,340),status=column("Status",AdminRow::status,90);
        table.getColumns().setAll(id,name,detail,status);
        TextField search=new TextField();search.setPromptText("Search "+section.toLowerCase()+"...");
        Button add=new Button("+ Add");add.getStyleClass().add("primary-button");add.setOnAction(e->add(section));
        Button toggle=new Button("Activate / Deactivate");toggle.getStyleClass().add("outline-button");toggle.setOnAction(e->toggle(section,table));
        HBox actions=new HBox(9,search,add,toggle);HBox.setHgrow(search,Priority.ALWAYS);
        VBox body=new VBox(12,actions,table);VBox.setVgrow(table,Priority.ALWAYS);body.getStyleClass().add("section-card");
        var source=FXCollections.<AdminRow>observableArrayList();sources.put(section,source);FilteredList<AdminRow> view=new FilteredList<>(source);filtered.put(section,view);table.setItems(view);
        search.textProperty().addListener((o,old,value)->view.setPredicate(row->value==null||value.isBlank()||row.searchText().contains(value.toLowerCase(Locale.ROOT))));
        if (section.equals("Coffee Products") && AppServices.liveAdminProducts().isPresent()) {
            Button open = new Button("Open Coffee Products");
            open.setId("openLiveProductsButton");
            open.getStyleClass().add("primary-button");
            open.setOnAction(event -> SceneNavigator.show(screenRoot,
                    "/com/coffeemongers/admin/CoffeeProducts.fxml", "Coffee Mongers · Coffee Products"));
            body.getChildren().setAll(new Label("Manage the shared PostgreSQL coffee catalog."), open);
        }
        return new Tab(section,body);
    }
    private Tab supplierProductsTab(){
        TableView<AdminRow> table=table("Supplier Products",new String[]{"Supplier","Product","Status"},new double[]{200,260,100});
        ComboBox<String> picker=new ComboBox<>();supplierProductPicker=picker;picker.setPromptText("Select existing product");picker.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(picker,Priority.ALWAYS);
        Button add=new Button("Add product");add.getStyleClass().add("primary-button");add.setOnAction(e->{String selected=picker.getValue();if(selected==null){error("Select a coffee product first.");return;}try{AppServices.marketplace().adminAddSupplierProduct(selected);picker.setValue(null);refresh();}catch(IllegalArgumentException ex){error(ex.getMessage());}});
        Button remove=new Button("Remove selected");remove.getStyleClass().add("outline-button");remove.setOnAction(e->{AdminRow row=table.getSelectionModel().getSelectedItem();if(row==null){error("Select a supplier product first.");return;}Alert confirmation=new Alert(Alert.AlertType.CONFIRMATION,"Remove "+row.id()+" from the demo supplier catalog?",ButtonType.CANCEL,ButtonType.OK);if(confirmation.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){AppServices.marketplace().adminRemoveSupplierProduct(row.id());refresh();}});
        VBox body=new VBox(10,new Label("Current supplier catalog links"),new HBox(9,picker,add,remove),table);VBox.setVgrow(table,Priority.ALWAYS);body.getStyleClass().add("section-card");tables.put("Supplier Products",table);return new Tab("Supplier Products",body);
    }
    private Tab supplyTab(){
        TableView<AdminRow> table=table("Supply Monitoring",new String[]{"Supplier","Product","Week / Year","Remaining"},new double[]{200,240,140,130});
        VBox body=monitorBody("Weekly supplier quantity · remaining supply is allocation controlled",table);tables.put("Supply Monitoring",table);return new Tab("Supply Monitoring",body);
    }
    private Tab reviewsTab(){
        TableView<AdminRow> table=table("Reviews",new String[]{"Supplier","Order","Criteria","Comment","Created"},new double[]{180,140,210,280,150});
        VBox body=monitorBody("Supplier feedback based on schema review criteria",table);tables.put("Reviews",table);return new Tab("Reviews",body);
    }
    private VBox monitorBody(String caption,TableView<AdminRow> table){VBox body=new VBox(10,new Label(caption),table);VBox.setVgrow(table,Priority.ALWAYS);body.getStyleClass().add("section-card");return body;}
    private TableView<AdminRow> table(String key,String[] titles,double[] widths){TableView<AdminRow> table=new TableView<>();table.getStyleClass().add("data-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);String[] props={"type","id","name","detail","status"};for(int i=0;i<titles.length;i++){final int column=i;table.getColumns().add(column(titles[i],row->switch(column){case 0->row.type();case 1->row.id();case 2->row.name();case 3->row.detail();default->row.status();},widths[i]));}table.setItems(FXCollections.observableArrayList());return table;}
    private Tab activityTab(){
        TableView<AdminRow> table=new TableView<>();table.getStyleClass().add("data-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(column("Type",AdminRow::type,130),column("Reference",AdminRow::id,150),column("Activity",AdminRow::name,330),column("Date",AdminRow::detail,130),column("Status",AdminRow::status,120));
        table.setItems(FXCollections.observableArrayList());
        VBox body=new VBox(10,new Label("Recent demand and order activity · demo records"),table);VBox.setVgrow(table,Priority.ALWAYS);body.getStyleClass().add("section-card");
        tables.put("Market Activity",table);return new Tab("Market Activity",body);
    }
    private TableColumn<AdminRow,String> column(String title,java.util.function.Function<AdminRow,String> value,double width){TableColumn<AdminRow,String> c=new TableColumn<>(title);c.setCellValueFactory(cell->new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));c.setPrefWidth(width);return c;}
    private void refresh(){
        var service=AppServices.marketplace();
        if(supplierProductPicker!=null)supplierProductPicker.setItems(FXCollections.observableArrayList(service.coffeeProducts()));
        sources.get("Coffee Shops").setAll(service.adminCoffeeShops().stream().map(s->new AdminRow("Coffee Shop",Long.toString(s.shopId()),s.name(),s.contactPerson()+" · "+s.phone()+" · "+s.email()+" · "+s.address(),s.isActive()?"Active":"Inactive")).toList());
        sources.get("Suppliers").setAll(service.adminSuppliers().stream().map(s->new AdminRow("Supplier",Long.toString(s.supplierId()),s.name(),s.contactPerson()+" · "+s.phone()+" · "+s.email()+" · "+s.address(),s.isActive()?"Active":"Inactive")).toList());
        sources.get("Coffee Products").setAll(service.adminCoffeeProducts().stream().map(p->new AdminRow("Coffee Product",Long.toString(p.productId()),p.name(),p.variety()+" · "+p.unit()+" · "+p.description(),p.isActive()?"Active":"Inactive")).toList());
        tables.get("Supplier Products").getItems().setAll(service.adminSupplierProducts().stream().map(name->new AdminRow("Cau Dat Coffee Farm",name,"Active",""," ")).toList());
        tables.get("Supply Monitoring").getItems().setAll(service.adminWeeklySupply().stream().map(row->new AdminRow(row.supplierId(),row.productName(),"Week "+row.week()+" · "+row.year(),row.remainingQuantity()+" "+row.unit(),"")).toList());
        tables.get("Reviews").getItems().setAll(service.adminReviews().stream().map(row->new AdminRow(row.supplierId(),row.orderId(),"Quality "+yes(row.qualityOk())+" · Quantity "+yes(row.quantityOk())+" · On time "+yes(row.onTimeOk()),row.comment(),row.createdOn().toString())).toList());
        List<AdminRow> activity=new ArrayList<>();service.adminRecentDemands().forEach(d->activity.add(new AdminRow("Demand",d.id(),d.title()+" · "+d.items().size()+" product line(s)",d.requestDate().toString(),"Submitted")));service.adminOrders().forEach(o->activity.add(new AdminRow("Order",o.id(),o.supplierName()+" · "+o.items().size()+" line(s)",o.createdOn().toString(),o.status())));tables.get("Market Activity").getItems().setAll(activity);
    }
    private void add(String section){
        Dialog<ButtonType> dialog=new Dialog<>();dialog.setTitle("Add "+section.substring(0,section.length()-1));dialog.setHeaderText("Create a demo record using the schema fields");dialog.getDialogPane().getButtonTypes().setAll(new ButtonType("Save",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);
        GridPane form=new GridPane();form.setHgap(10);form.setVgap(9);Map<String,TextField> fields=new LinkedHashMap<>();String[] labels=section.equals("Coffee Shops")?new String[]{"Name","Contact person","Phone","Email","Address"}:section.equals("Suppliers")?new String[]{"Name","Contact person","Phone","Email","Address"}:new String[]{"Name","Description","Unit","Variety"};
        for(int i=0;i<labels.length;i++){TextField input=new TextField();input.setPromptText(labels[i]);fields.put(labels[i],input);form.add(new Label(labels[i]),0,i);form.add(input,1,i);}
        dialog.getDialogPane().setContent(form);Window owner=screenRoot.getScene()==null?null:screenRoot.getScene().getWindow();if(owner!=null)dialog.initOwner(owner);
        dialog.showAndWait().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).ifPresent(b->{try{var s=AppServices.marketplace();if(section.equals("Coffee Shops"))s.addCoffeeShop(v(fields,"Name"),v(fields,"Contact person"),v(fields,"Phone"),v(fields,"Email"),v(fields,"Address"));else if(section.equals("Suppliers"))s.addSupplier(v(fields,"Name"),v(fields,"Contact person"),v(fields,"Phone"),v(fields,"Email"),v(fields,"Address"));else s.addCoffeeProduct(v(fields,"Name"),v(fields,"Description"),v(fields,"Unit"),v(fields,"Variety"));refresh();}catch(IllegalArgumentException ex){error(ex.getMessage());}});
    }
    private void toggle(String section,TableView<AdminRow> table){AdminRow row=table.getSelectionModel().getSelectedItem();if(row==null){error("Select a record first.");return;}boolean active=row.status().equals("Active");ButtonType proceed=new ButtonType(active?"Deactivate":"Activate",ButtonBar.ButtonData.OK_DONE);Alert confirmation=new Alert(Alert.AlertType.CONFIRMATION,(active?"Deactivate ":"Activate ")+row.name()+"?",proceed,ButtonType.CANCEL);if(confirmation.showAndWait().orElse(ButtonType.CANCEL)!=proceed)return;long id=Long.parseLong(row.id());var service=AppServices.marketplace();if(section.equals("Coffee Shops"))service.setCoffeeShopActive(id,!active);else if(section.equals("Suppliers"))service.setSupplierActive(id,!active);else service.setCoffeeProductActive(id,!active);UndoHistory.register(screenRoot,(active?"activation of ":"deactivation of ")+row.name(),()->{if(section.equals("Coffee Shops"))service.setCoffeeShopActive(id,active);else if(section.equals("Suppliers"))service.setSupplierActive(id,active);else service.setCoffeeProductActive(id,active);refresh();},message->{error(message);});refresh();}
    private static String v(Map<String,TextField> fields,String key){return fields.get(key).getText().trim();}
    private static String yes(boolean value){return value?"Yes":"No";}
    private void error(String message){new Alert(Alert.AlertType.INFORMATION,message,ButtonType.OK).showAndWait();}
    @FXML private void openOverview(){SceneNavigator.back(screenRoot,"/com/coffeemongers/admin/AdminDashboard.fxml","Coffee Mongers · Admin Dashboard");}
    public record AdminRow(String type,String id,String name,String detail,String status){public String searchText(){return (type+" "+id+" "+name+" "+detail+" "+status).toLowerCase(Locale.ROOT);}}
}
