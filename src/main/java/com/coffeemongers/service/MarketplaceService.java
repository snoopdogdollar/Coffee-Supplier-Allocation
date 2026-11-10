package com.coffeemongers.service;

import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.model.OrderRecord;
import com.coffeemongers.model.SupplierMatch;
import com.coffeemongers.model.SupplierReview;
import com.coffeemongers.model.WeeklySupplyRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.coffeemongers.model.schema.CoffeeShop;
import com.coffeemongers.model.schema.Supplier;
import com.coffeemongers.model.schema.CoffeeProduct;
import com.coffeemongers.model.viewmodel.SupplierAdvertisement;
import com.coffeemongers.model.viewmodel.SupplierDirectoryEntry;

/** UI-facing boundary. Replace its implementation with an HTTP API client when a backend is available. */
public interface MarketplaceService {
    boolean isDemoMode();
    List<String> coffeeProducts();
    String coffeeProductUnit(String productName);
    List<DemandRequest> recentDemands();
    DemandRequest demand(String demandId);
    DemandRequest submitDemand(DemandRequest request);
    List<SupplierMatch> recommendations(String demandId);
    List<OrderRecord> confirmAllocations(String demandId, Map<String, BigDecimal> quantities);
    List<OrderRecord> buyerOrders();
    void submitReview(SupplierReview review);
    List<WeeklySupplyRecord> weeklySupply(LocalDate weekStart);
    WeeklySupplyRecord saveWeeklySupply(String id, String product, LocalDate weekStart,
                                        BigDecimal quantity, BigDecimal unitPrice);
    void deleteWeeklySupply(String id);
    List<String> supplierProducts();
    boolean addSupplierProduct(String productName);
    boolean removeSupplierProduct(String productName);
    List<OrderRecord> supplierOrders();
    List<OrderRecord> adminOrders();
    void markOrderDelivered(String orderId);
    List<CoffeeShop> adminCoffeeShops();
    List<Supplier> adminSuppliers();
    List<CoffeeProduct> adminCoffeeProducts();
    List<String> adminSupplierProducts();
    boolean adminAddSupplierProduct(String productName);
    boolean adminRemoveSupplierProduct(String productName);
    List<DemandRequest> adminRecentDemands();
    CoffeeShop addCoffeeShop(String name, String contactPerson, String phone, String email, String address);
    Supplier addSupplier(String name, String contactPerson, String phone, String email, String address);
    CoffeeProduct addCoffeeProduct(String name, String description, String unit, String variety);
    void setCoffeeShopActive(long shopId, boolean active);
    void setSupplierActive(long supplierId, boolean active);
    void setCoffeeProductActive(long productId, boolean active);
    List<WeeklySupplyRecord> adminWeeklySupply();
    List<SupplierReview> adminReviews();
    List<SupplierAdvertisement> featuredSupplierAdvertisements();
    List<SupplierDirectoryEntry> buyerSupplierDirectory();
}
