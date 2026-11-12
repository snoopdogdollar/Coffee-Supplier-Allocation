package com.coffeemongers;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.event.ActionEvent;
import javafx.scene.control.TabPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import com.coffeemongers.service.AppServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class FxmlLoadTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) fail("JavaFX toolkit did not start");
    }

    @Test
    void everyFxmlViewLoadsWithItsController() throws Exception {
        Path views = Path.of(System.getProperty("user.dir"), "src/main/resources/com/coffeemongers");
        if (!Files.isDirectory(views)) fail("Resource directory not found: " + views);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);

        Platform.runLater(() -> {
            try (var paths = Files.walk(views)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                    String classpathResource = "/com/coffeemongers/" + views.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                    URL resource = getClass().getResource(classpathResource);
                    if (resource == null) throw new AssertionError("Missing classpath resource " + classpathResource);
                    FXMLLoader loader = new FXMLLoader(resource);
                    loader.load();
                    if (loader.getController() == null) throw new AssertionError("Missing controller for " + path);
                    if(path.getFileName().toString().equals("AuthHero.fxml")){ImageView hero=(ImageView)loader.getNamespace().get("heroImage");if(hero.getImage()==null||hero.getImage().isError())throw new AssertionError("Coffee farm background did not load from Maven resources.");}
                    count.incrementAndGet();
                }
                verifySidebarNavigation();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });

        if (!completed.await(90, TimeUnit.SECONDS)) fail("FXML load and navigation check timed out");
        if (failure.get() != null) fail("FXML load failed", failure.get());
        assertEquals(22, count.get());
    }

    private void verifySidebarNavigation() throws Exception {
        Stage stage=new Stage();
        try {
            AppServices.session().start(new DemoUser("nav-buyer","Navigation Buyer","navigation-buyer@example.test",AccountRole.BUYER));
            Parent buyer=show(stage,"/com/coffeemongers/view/BuyerDashboard.fxml","Buyer Dashboard");
            fire(buyer,"overviewNavButton");assertTitle(stage,"Buyer Dashboard");buyer=stage.getScene().getRoot();
            fire(buyer,"createDemandNavButton");assertTitle(stage,"Create Demand");
            Parent create=stage.getScene().getRoot();VBox rows=(VBox)create.lookup("#productRowsContainer");assertEquals(1,rows.getChildren().size());fire(create,"addProductButton");assertEquals(2,rows.getChildren().size());
            fillProductRow((GridPane) rows.getChildren().get(0), 0, "120");
            fillProductRow((GridPane) rows.getChildren().get(1), 1, "80");
            assertVisibleFooterAction(stage, "reviewDemandButton");
            fire(create, "reviewDemandButton");
            assertTitle(stage, "Demand Details");
            assertVisibleFooterAction(stage, "findMatchesButton");
            fire(stage.getScene().getRoot(), "findMatchesButton");
            assertTitle(stage, "Matching Results");
            assertEquals(false, ((VBox) stage.getScene().getRoot().lookup("#matchesContainer")).getChildren().isEmpty());
            fire(stage.getScene().getRoot(), "homeButton");
            buyer=stage.getScene().getRoot();fire(buyer,"matchingNavButton");assertTitle(stage,"Matching Results");assertEquals("#DM-004",((Label)stage.getScene().getRoot().lookup("#demandIdLabel")).getText());fireClass(stage.getScene().getRoot(),"back-button");
            buyer=stage.getScene().getRoot();fire(buyer,"suppliersNavButton");assertTitle(stage,"Suppliers");fire(stage.getScene().getRoot(),"homeButton");assertTitle(stage,"Buyer Dashboard");
            buyer=stage.getScene().getRoot();fire(buyer,"profileNavButton");assertTitle(stage,"Buyer Profile");fireClass(stage.getScene().getRoot(),"back-button");
            buyer=stage.getScene().getRoot();fire(buyer,"ordersNavButton");assertTitle(stage,"My Orders");fire(stage.getScene().getRoot(),"createDemandButton");assertTitle(stage,"Create Demand");fireClass(stage.getScene().getRoot(),"back-button");

            AppServices.session().start(new DemoUser("nav-admin","Navigation Admin","navigation-admin@example.test",AccountRole.ADMIN));
            Parent admin=show(stage,"/com/coffeemongers/admin/AdminDashboard.fxml","Admin Dashboard");
            fire(admin,"overviewNav");assertTitle(stage,"Admin Dashboard");admin=stage.getScene().getRoot();
            String[][] adminItems={{"activityNav","Market Activity"},{"shopsNav","Coffee Shops"},{"suppliersNav","Suppliers"},{"productsNav","Coffee Products"},{"supplierProductsNav","Supplier Products"},{"supplyNav","Supply Monitoring"},{"reviewsNav","Reviews"}};
            for(String[] item:adminItems){fire(admin,item[0]);assertTitle(stage,"Marketplace Management");TabPane tabs=(TabPane)stage.getScene().getRoot().lookup("#managementTabs");assertEquals(item[1],tabs.getSelectionModel().getSelectedItem().getText());fireClass(stage.getScene().getRoot(),"back-button");admin=stage.getScene().getRoot();}
            assertEquals(false, admin.lookup("#allocationPreviewNav").isVisible());
            fire(admin,"activityNav");fire(stage.getScene().getRoot(),"homeButton");assertTitle(stage,"Admin Dashboard");admin=stage.getScene().getRoot();
            fire(admin,"buyerButton");assertTitle(stage,"Buyer Dashboard");assertEquals(AccountRole.ADMIN,AppServices.currentUser().orElseThrow().role());assertEquals(AccountRole.BUYER,AppServices.session().activeWorkspace());assertEquals(false,((Button)stage.getScene().getRoot().lookup("#createDemandNavButton")).isDisabled());fire(stage.getScene().getRoot(),"createDemandNavButton");assertEquals(true,((Button)stage.getScene().getRoot().lookup("#reviewDemandButton")).isDisabled());fireClass(stage.getScene().getRoot(),"back-button");
            fire(stage.getScene().getRoot(),"adminButton");assertTitle(stage,"Admin Dashboard");assertEquals(AccountRole.ADMIN,AppServices.currentUser().orElseThrow().role());assertEquals(AccountRole.ADMIN,AppServices.session().activeWorkspace());
            fire(stage.getScene().getRoot(),"logoutButton");assertTitle(stage,"Sign in");assertEquals(true,AppServices.currentUser().isEmpty());

            AppServices.session().start(new DemoUser("nav-supplier","Navigation Supplier","navigation-supplier@example.test",AccountRole.SUPPLIER));
            Parent supplier=show(stage,"/com/coffeemongers/supplier/SupplierDashboard.fxml","Supplier Dashboard");
            String[][] supplierItems={{"weeklySupplyNav","Weekly Supply"},{"ordersNav","Supplier Orders"},{"productsNav","Supplier Products"}};
            for(String[] item:supplierItems){fire(supplier,item[0]);assertTitle(stage,item[1]);if(item[0].equals("weeklySupplyNav")){fire(stage.getScene().getRoot(),"homeButton");assertTitle(stage,"Supplier Dashboard");}else fireClass(stage.getScene().getRoot(),"back-button");supplier=stage.getScene().getRoot();}
            fire(supplier,"dashboardNav");assertTitle(stage,"Supplier Dashboard");
            fire(stage.getScene().getRoot(),"logoutButton");assertTitle(stage,"Sign in");
        } finally {AppServices.session().end();stage.close();}
    }

    private static void assertVisibleFooterAction(Stage stage, String buttonId) {
        stage.setHeight(720);
        Scene scene = stage.getScene();
        BorderPane root = (BorderPane) scene.getRoot();
        root.applyCss();
        root.layout();

        if (root.getBottom() == null || !(root.getBottom().lookup("#" + buttonId) instanceof Button button)
                || !button.isVisible() || button.isDisabled()) {
            fail("Expected an enabled action #" + buttonId + " in the fixed footer");
            return;
        }
        var bounds = button.localToScene(button.getBoundsInLocal());
        if (bounds.getMinY() < 0 || bounds.getMaxY() > scene.getHeight() || bounds.getHeight() <= 0) {
            fail("Action #" + buttonId + " must fit in the visible scene");
        }
    }

    private Parent show(Stage stage,String resource,String title)throws Exception{Parent root=FXMLLoader.load(getClass().getResource(resource));Scene scene=new Scene(root,1280,800);var css=getClass().getResource("/com/coffeemongers/view/buyer-dashboard.css");if(css!=null)scene.getStylesheets().add(css.toExternalForm());stage.setScene(scene);stage.setTitle("Coffee Mongers · "+title);if(!stage.isShowing())stage.show();return root;}
    private static void fillProductRow(GridPane row,int productIndex,String quantity){ComboBox<String> product=(ComboBox<String>)row.getChildren().get(0);product.setValue(product.getItems().get(productIndex));((TextField)row.getChildren().get(1)).setText(quantity);}
    private static void fire(Parent root,String id){var node=root.lookup("#"+id);if(!(node instanceof Button button))throw new AssertionError("Missing navigation button #"+id);if(button.isDisabled())throw new AssertionError("Navigation button #"+id+" is disabled");if(button.getOnAction()==null)throw new AssertionError("Navigation button #"+id+" has no action handler");button.getOnAction().handle(new ActionEvent(button,button));}
    private static void fireClass(Parent root,String styleClass){var node=root.lookup("."+styleClass);if(!(node instanceof Button button))throw new AssertionError("Missing button with style class "+styleClass);if(button.getOnAction()==null)throw new AssertionError("Button with style class "+styleClass+" has no action handler");button.getOnAction().handle(new ActionEvent(button,button));}
    private static void assertTitle(Stage stage,String expected){if(!stage.getTitle().contains(expected))throw new AssertionError("Expected "+expected+" route but found "+stage.getTitle());}

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }
}
