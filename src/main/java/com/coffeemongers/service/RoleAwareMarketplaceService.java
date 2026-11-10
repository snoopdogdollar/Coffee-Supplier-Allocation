package com.coffeemongers.service;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;

/** Enforces workspace access at the service boundary while preserving the UI-facing interface. */
final class RoleAwareMarketplaceService {
    private static final Set<String> BUYER_WRITES=Set.of("submitDemand","confirmAllocations","submitReview");
    private static final Set<String> SUPPLIER_WRITES=Set.of("saveWeeklySupply","deleteWeeklySupply","addSupplierProduct","removeSupplierProduct","markOrderDelivered");
    private static final Set<String> ADMIN_WRITES=Set.of("addCoffeeShop","addSupplier","addCoffeeProduct","setCoffeeShopActive","setSupplierActive","setCoffeeProductActive","adminAddSupplierProduct","adminRemoveSupplierProduct");
    private RoleAwareMarketplaceService() { }
    static MarketplaceService protect(MarketplaceService target) {
        return (MarketplaceService)Proxy.newProxyInstance(MarketplaceService.class.getClassLoader(),new Class<?>[]{MarketplaceService.class},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return method.invoke(target,args);
            AccountRole required=requiredRole(method.getName());
            if(required!=null){
                DemoUser user=AppServices.currentUser().orElse(null);
                AccountRole workspace=AppServices.session().activeWorkspace();
                // Allow read-only FXML previews before a user signs in; the app's entry screen is Login.
                if(workspace!=null&&workspace!=required)throw new SecurityException("Switch to the "+required+" workspace to access this action.");
                if(user!=null&&user.role()!=AccountRole.ADMIN&&user.role()!=required)throw new SecurityException("Your account does not have access to this workspace.");
                boolean write=BUYER_WRITES.contains(method.getName())||SUPPLIER_WRITES.contains(method.getName())||ADMIN_WRITES.contains(method.getName());
                if(write&&user==null)throw new SecurityException("Sign in before changing marketplace data.");
                if(write&&user!=null&&user.role()!=required)throw new SecurityException("Workspace preview is read-only for this account.");
            }
            try{return method.invoke(target,args);}catch(InvocationTargetException exception){throw exception.getCause();}
        });
    }
    private static AccountRole requiredRole(String method){
        if(method.startsWith("admin")||method.startsWith("addCoffee")||method.startsWith("setCoffee")||method.startsWith("setSupplier"))return AccountRole.ADMIN;
        if(method.startsWith("supplier")||method.equals("weeklySupply")||method.equals("saveWeeklySupply")||method.equals("deleteWeeklySupply")||method.equals("addSupplierProduct")||method.equals("removeSupplierProduct")||method.equals("markOrderDelivered"))return AccountRole.SUPPLIER;
        if(method.equals("recentDemands")||method.equals("demand")||method.equals("submitDemand")||method.equals("recommendations")||method.equals("confirmAllocations")||method.equals("buyerOrders")||method.equals("submitReview")||method.equals("buyerSupplierDirectory"))return AccountRole.BUYER;
        return null;
    }
}
