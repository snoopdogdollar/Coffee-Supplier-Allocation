package com.coffeemongers.view;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import javafx.scene.Node;
import com.coffeemongers.service.AppServices;

public final class WorkspaceRouter {
    private WorkspaceRouter(){ }
    public static void open(Node source,DemoUser user){
        String resource=switch(user.role()){
            case ADMIN->"/com/coffeemongers/admin/AdminDashboard.fxml";
            case BUYER->"/com/coffeemongers/view/BuyerDashboard.fxml";
            case SUPPLIER->"/com/coffeemongers/supplier/SupplierDashboard.fxml";
        };
        String title="Coffee Mongers · "+switch(user.role()){case ADMIN->"Admin Dashboard";case BUYER->"Buyer Dashboard";case SUPPLIER->"Supplier Dashboard";};
        SceneNavigator.clearAndShow(source,resource,title);
    }
    public static void openHome(Node source) {
        AccountRole workspace=AppServices.session().activeWorkspace();
        if(workspace==null) return;
        String resource=switch(workspace){case ADMIN->"/com/coffeemongers/admin/AdminDashboard.fxml";case BUYER->"/com/coffeemongers/view/BuyerDashboard.fxml";case SUPPLIER->"/com/coffeemongers/supplier/SupplierDashboard.fxml";};
        String title="Coffee Mongers · "+switch(workspace){case ADMIN->"Admin Dashboard";case BUYER->"Buyer Dashboard";case SUPPLIER->"Supplier Dashboard";};
        SceneNavigator.clearAndShow(source,resource,title);
    }
}
