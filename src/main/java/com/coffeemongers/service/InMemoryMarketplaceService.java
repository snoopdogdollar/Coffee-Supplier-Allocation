package com.coffeemongers.service;

import com.coffeemongers.model.*;
import com.coffeemongers.model.schema.CoffeeShop;
import com.coffeemongers.model.schema.Supplier;
import com.coffeemongers.model.schema.CoffeeProduct;
import com.coffeemongers.model.viewmodel.SupplierAdvertisement;
import com.coffeemongers.model.viewmodel.SupplierDirectoryEntry;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/** Clearly marked local demo data; nothing here is loaded from PostgreSQL. */
public final class InMemoryMarketplaceService implements MarketplaceService {
    private final DemoOrderHub orderHub;
    private final List<CoffeeShop> adminShops = new ArrayList<>();
    private final List<Supplier> adminSupplierRecords = new ArrayList<>();
    private final List<CoffeeProduct> adminProductRecords = new ArrayList<>();
    private List<String> products = List.of(
            "Robusta - Natural", "Robusta - Honey", "Robusta - Washed",
            "Arabica - Natural", "Arabica - Honey", "Arabica - Washed");
    private final List<DemandRequest> demands = new ArrayList<>();
    private final List<OrderRecord> orders = new ArrayList<>();
    private final List<WeeklySupplyRecord> supply = new ArrayList<>();
    private final List<SupplierReview> reviews = new ArrayList<>();
    private final Set<String> supplierCatalog = new LinkedHashSet<>();
    private final Map<String, List<SupplierMatch>> matchesByDemand = new HashMap<>();
    private int demandSequence = 4;
    private int orderSequence = 1;
    private int supplySequence = 1;

    public InMemoryMarketplaceService() { this(new DemoOrderHub()); }
    public InMemoryMarketplaceService(DemoOrderHub orderHub) {
        this.orderHub=Objects.requireNonNull(orderHub);
        LocalDateTime now = LocalDateTime.now();
        adminShops.add(new CoffeeShop(1, "Bếp Cà Phê", "Nguyen Minh Anh", "+84 28 3822 1100", "hello@bepcafe.example", "District 3, Ho Chi Minh City", true, now));
        adminSupplierRecords.add(new Supplier(1, "Cau Dat Coffee Farm", "Tran Duc Long", "+84 263 355 1200", "supply@caudat.example", "Da Lat, Lam Dong", true, now));
        adminSupplierRecords.add(new Supplier(2, "Dak Lak Coffee Co-op", "Le Thi Hoa", "+84 262 366 9800", "sales@daklak.example", "Buon Ma Thuot, Dak Lak", true, now));
        adminSupplierRecords.add(new Supplier(3, "Gia Lai Coffee Supply", "Pham Van Nam", "+84 269 377 0500", "contact@gialai.example", "Pleiku, Gia Lai", true, now));
        for (int i = 0; i < products.size(); i++) {
            String product = products.get(i); String[] split = product.split(" - ", 2);
            adminProductRecords.add(new CoffeeProduct(i + 1L, product, split[0] + " coffee, " + split[1].toLowerCase(Locale.ROOT) + " process", "kg", split[0], true, now));
        }
        LocalDate week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        seedSupplier("SUP-001", "Cau Dat Coffee Farm", "Da Lat, Lam Dong", "Arabica, Cau Dat", "120000", "92");
        seedSupplier("SUP-002", "Dak Lak Coffee Co-op", "Buon Ma Thuot, Dak Lak", "Robusta", "98000", "88");
        seedSupplier("SUP-003", "Gia Lai Coffee Supply", "Pleiku, Gia Lai", "Robusta, Arabica", "105000", "85");
        for (WeeklySupplyRecord row : List.copyOf(supply)) {
            supply.set(supply.indexOf(row), new WeeklySupplyRecord(row.id(), row.supplierId(), row.productName(), week,
                    row.initialQuantity(), row.remainingQuantity(), row.unitPrice(), row.unit()));
        }
        demands.add(new DemandRequest("#DM-001", "November Robusta", LocalDate.now().plusDays(10),
                "Bếp Cà Phê, Quận 3", "Demo preview record", List.of(new DemandItem(products.get(0), BigDecimal.valueOf(500), "kg"))));
        demands.add(new DemandRequest("#DM-002", "Arabica restock", LocalDate.now().plusDays(14),
                "Bếp Cà Phê, Quận 3", "Demo preview record", List.of(new DemandItem(products.get(5), BigDecimal.valueOf(200), "kg"))));
    }

    private void seedSupplier(String id, String name, String location, String varieties, String price, String score) {
        String[] supported = name.startsWith("Cau Dat") ? new String[]{products.get(5), products.get(3)}
                : name.startsWith("Dak Lak") ? new String[]{products.get(0), products.get(1), products.get(2)}
                : products.toArray(String[]::new);
        for (String product : supported) {
            if (id.equals("SUP-001")) supplierCatalog.add(product);
            BigDecimal qty = name.startsWith("Cau Dat") ? BigDecimal.valueOf(1200) : BigDecimal.valueOf(800);
            supply.add(new WeeklySupplyRecord("SUPPLY-%03d".formatted(supplySequence++), id, product,
                    LocalDate.now(), qty, qty, new BigDecimal(price)));
        }
        supplierNames.put(id, name);
        supplierLocations.put(id, location);
        supplierVarieties.put(id, varieties);
        supplierScores.put(id, new BigDecimal(score));
    }

    private final Map<String, String> supplierNames = new LinkedHashMap<>();
    private final Map<String, String> supplierLocations = new HashMap<>();
    private final Map<String, String> supplierVarieties = new HashMap<>();
    private final Map<String, BigDecimal> supplierScores = new HashMap<>();

    @Override public boolean isDemoMode() { return true; }
    @Override public List<String> coffeeProducts() { return products; }
    @Override public String coffeeProductUnit(String productName) {
        return adminProductRecords.stream().filter(p->p.name().equals(productName)).findFirst().map(CoffeeProduct::unit)
                .orElseThrow(()->new IllegalArgumentException("Choose an existing coffee product."));
    }
    @Override public List<DemandRequest> recentDemands() { return List.copyOf(demands); }
    @Override public DemandRequest demand(String demandId) { return findDemand(demandId); }

    @Override public DemandRequest submitDemand(DemandRequest request) {
        Set<String> distinctProducts=new HashSet<>();
        for(DemandItem item:request.items())if(!distinctProducts.add(item.productName()))throw new IllegalArgumentException("Each coffee product can appear once in a demand.");
        String id = "#DM-%03d".formatted(demandSequence++);
        DemandRequest saved = new DemandRequest(id, request.title(), request.requestDate(),
                request.shopAddress(), request.notes(), request.items());
        demands.add(0, saved);
        return saved;
    }

    @Override public List<SupplierMatch> recommendations(String demandId) {
        if (matchesByDemand.containsKey(demandId)) return matchesByDemand.get(demandId);
        DemandRequest demand = findDemand(demandId);
        List<SupplierMatch> matches = new ArrayList<>();
        for (DemandItem item : demand.items()) {
            BigDecimal requested = item.quantity();
            for (String supplierId : supplierNames.keySet()) {
                // Each supplier is presented only when its own current demo stock supports the product.
                BigDecimal supplierAvailable = supplierSupply(supplierId, item.productName());
                if (supplierAvailable.signum() <= 0) continue;
                matches.add(new SupplierMatch(supplierId + "-" + item.productName(), supplierId,
                        supplierNames.get(supplierId), supplierLocations.get(supplierId), item.productName(),
                        supplierAvailable.min(requested), priceFor(supplierId, item.productName()), supplierScores.get(supplierId), item.unit()));
            }
        }
        List<SupplierMatch> result = List.copyOf(matches);
        matchesByDemand.put(demandId, result);
        return result;
    }

    private BigDecimal supplierSupply(String supplierId, String product) {
        // In this isolated mock, supplier ownership is defined by the seeded product catalog below.
        Set<String> supported = supplierId.equals("SUP-001") ? supplierCatalog
                : supplierId.equals("SUP-002") ? Set.of(products.get(0), products.get(1), products.get(2))
                : Set.copyOf(products);
        if (!supported.contains(product)) return BigDecimal.ZERO;
        return supply.stream().filter(row -> row.supplierId().equals(supplierId) && row.productName().equals(product))
                .map(WeeklySupplyRecord::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal priceFor(String supplierId, String product) {
        return supplierId.equals("SUP-001") ? BigDecimal.valueOf(125000)
                : supplierId.equals("SUP-002") ? BigDecimal.valueOf(98000) : BigDecimal.valueOf(105000);
    }

    @Override public List<OrderRecord> confirmAllocations(String demandId, Map<String, BigDecimal> quantities) {
        DemandRequest demand = findDemand(demandId);
        List<SupplierMatch> matches = recommendations(demandId);
        Map<String, BigDecimal> totalsByProduct = new HashMap<>();
        Map<String, SupplierMatch> matchById = matches.stream().collect(Collectors.toMap(SupplierMatch::id, m -> m));
        for (var entry : quantities.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() < 0) throw new IllegalArgumentException("Allocation quantities cannot be negative.");
            if (entry.getValue().signum() == 0) continue;
            SupplierMatch match = matchById.get(entry.getKey());
            if (match == null) throw new IllegalArgumentException("An allocation no longer matches the current recommendations.");
            if (entry.getValue().compareTo(match.availableQuantity()) > 0) throw new IllegalArgumentException("Allocation exceeds available quantity for " + match.supplierName() + ".");
            totalsByProduct.merge(match.productName(), entry.getValue(), BigDecimal::add);
        }
        for (DemandItem item : demand.items()) {
            BigDecimal allocated = totalsByProduct.getOrDefault(item.productName(), BigDecimal.ZERO);
            if (allocated.compareTo(item.quantity()) != 0) throw new IllegalArgumentException("Allocate the full requested quantity for " + item.productName() + " before confirming.");
        }
        List<OrderRecord> created = new ArrayList<>();
        for (var entry : quantities.entrySet()) {
            if(entry.getValue()==null||entry.getValue().signum()<=0)continue;
            SupplierMatch match=matchById.get(entry.getKey());
            OrderItem item=new OrderItem(match.productName(),entry.getValue(),match.unitPrice(),match.unit());
            OrderRecord order = new OrderRecord("#ORD-DEMO-%03d".formatted(orderSequence++), demandId,
                    match.supplierId(), supplierNames.get(match.supplierId()), LocalDate.now(), "Confirmed", List.of(item), false);
            orders.add(0, order);
            orderHub.publish(order);
            created.add(order);
            consumeSupply(match.supplierId(), item.productName(), item.quantity());
        }
        return List.copyOf(created);
    }

    private void consumeSupply(String supplierId, String product, BigDecimal quantity) {
        for (int i = 0; i < supply.size(); i++) {
            WeeklySupplyRecord row = supply.get(i);
            if (!row.supplierId().equals(supplierId) || !row.productName().equals(product)) continue;
            BigDecimal consumed = quantity.min(row.remainingQuantity());
            supply.set(i, new WeeklySupplyRecord(row.id(), row.supplierId(), row.productName(), row.weekStart(),
                    row.initialQuantity(), row.remainingQuantity().subtract(consumed), row.unitPrice(), row.unit()));
            quantity = quantity.subtract(consumed);
            if (quantity.signum() == 0) return;
        }
    }

    private DemandRequest findDemand(String id) {
        return demands.stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Demand not found: " + id));
    }

    @Override public List<OrderRecord> buyerOrders() { return orders.stream().map(local->orderHub.find(local.id()).map(shared->new OrderRecord(local.id(),local.demandId(),local.supplierId(),local.supplierName(),local.createdOn(),shared.status(),local.items(),local.reviewed())).orElse(local)).toList(); }
    @Override public void submitReview(SupplierReview review) {
        int at=-1; for(int i=0;i<orders.size();i++) if(orders.get(i).id().equals(review.orderId())) at=i;
        if(at<0) throw new IllegalArgumentException("Order not found in this demo session.");
        OrderRecord old=orderHub.find(review.orderId()).orElse(orders.get(at));
        if(old.reviewed()) throw new IllegalArgumentException("A review has already been submitted for this order.");
        if(!"Delivered".equalsIgnoreCase(old.status()))throw new IllegalArgumentException("You can review this supplier after the order is marked delivered.");
        reviews.add(review);
        orders.set(at,new OrderRecord(old.id(),old.demandId(),old.supplierId(),old.supplierName(),old.createdOn(),old.status(),old.items(),true));
        orderHub.publish(orders.get(at));
    }

    @Override public List<WeeklySupplyRecord> weeklySupply(LocalDate weekStart) {
        return supply.stream().filter(row -> row.supplierId().equals("SUP-001") && row.weekStart().equals(weekStart)).toList();
    }

    @Override public WeeklySupplyRecord saveWeeklySupply(String id, String product, LocalDate weekStart,
                                                          BigDecimal quantity, BigDecimal unitPrice) {
        if (!products.contains(product)) throw new IllegalArgumentException("Choose an existing coffee product.");
        if (quantity.signum() <= 0 || unitPrice.signum() <= 0) throw new IllegalArgumentException("Quantity and unit price must be greater than zero.");
        int at = -1;
        if (id != null) for (int i=0;i<supply.size();i++) if (supply.get(i).id().equals(id)) at=i;
        WeeklySupplyRecord old = at < 0 ? null : supply.get(at);
        if (old == null && supply.stream().anyMatch(r -> r.productName().equals(product) && r.weekStart().equals(weekStart)))
            throw new IllegalArgumentException("A weekly record already exists for this product and week. Select it to update.");
        String recordId = old == null ? "SUPPLY-%03d".formatted(supplySequence++) : old.id();
        BigDecimal remaining = old == null ? quantity : old.remainingQuantity().add(quantity.subtract(old.initialQuantity())).max(BigDecimal.ZERO);
        WeeklySupplyRecord saved = new WeeklySupplyRecord(recordId, "SUP-001", product, weekStart, quantity, remaining, unitPrice, coffeeProductUnit(product));
        if (at < 0) supply.add(saved); else supply.set(at, saved);
        supplierCatalog.add(product);
        return saved;
    }

    @Override public void deleteWeeklySupply(String id) {
        supply.removeIf(row -> row.id().equals(id));
    }

    @Override public List<String> supplierProducts() { return List.copyOf(supplierCatalog); }

    @Override public boolean addSupplierProduct(String productName) {
        if (!products.contains(productName)) throw new IllegalArgumentException("Choose a product from the existing coffee catalog.");
        return supplierCatalog.add(productName);
    }

    @Override public boolean removeSupplierProduct(String productName) { return supplierCatalog.remove(productName); }

    @Override public List<OrderRecord> supplierOrders() { return orderHub.forSupplier("SUP-001"); }
    @Override public List<OrderRecord> adminOrders(){return orderHub.all();}
    @Override public void markOrderDelivered(String orderId){OrderRecord order=orderHub.find(orderId).orElseThrow(()->new IllegalArgumentException("Order not found."));if(!"SUP-001".equals(order.supplierId()))throw new IllegalArgumentException("This order belongs to another supplier.");if(!"Confirmed".equalsIgnoreCase(order.status()))throw new IllegalArgumentException("Only confirmed orders can be marked delivered.");orderHub.updateStatus(orderId,"Delivered");}

    @Override public List<CoffeeShop> adminCoffeeShops() { return List.copyOf(adminShops); }
    @Override public List<Supplier> adminSuppliers() { return List.copyOf(adminSupplierRecords); }
    @Override public List<CoffeeProduct> adminCoffeeProducts() { return List.copyOf(adminProductRecords); }
    @Override public List<String> adminSupplierProducts(){return List.copyOf(supplierCatalog);}
    @Override public boolean adminAddSupplierProduct(String productName){return addSupplierProduct(productName);}
    @Override public boolean adminRemoveSupplierProduct(String productName){return removeSupplierProduct(productName);}
    @Override public List<DemandRequest> adminRecentDemands(){return List.copyOf(demands);}
    @Override public CoffeeShop addCoffeeShop(String name, String contactPerson, String phone, String email, String address) {
        requireText(name,"Shop name"); CoffeeShop row=new CoffeeShop(nextShopId(),name,contactPerson,phone,email,address,true,LocalDateTime.now());adminShops.add(row);return row;
    }
    @Override public Supplier addSupplier(String name, String contactPerson, String phone, String email, String address) {
        requireText(name,"Supplier name"); Supplier row=new Supplier(nextSupplierId(),name,contactPerson,phone,email,address,true,LocalDateTime.now());adminSupplierRecords.add(row);return row;
    }
    @Override public CoffeeProduct addCoffeeProduct(String name, String description, String unit, String variety) {
        requireText(name,"Product name");requireText(unit,"Unit");requireText(variety,"Variety");CoffeeProduct row=new CoffeeProduct(adminProductRecords.stream().mapToLong(CoffeeProduct::productId).max().orElse(0)+1,name,description,unit,variety,true,LocalDateTime.now());adminProductRecords.add(row);productsWith(row.name());return row;
    }
    @Override public void setCoffeeShopActive(long id,boolean active){int i=indexShop(id);CoffeeShop r=adminShops.get(i);adminShops.set(i,new CoffeeShop(r.shopId(),r.name(),r.contactPerson(),r.phone(),r.email(),r.address(),active,r.createdAt()));}
    @Override public void setSupplierActive(long id,boolean active){int i=indexSupplier(id);Supplier r=adminSupplierRecords.get(i);adminSupplierRecords.set(i,new Supplier(r.supplierId(),r.name(),r.contactPerson(),r.phone(),r.email(),r.address(),active,r.createdAt()));}
    @Override public void setCoffeeProductActive(long id,boolean active){int i=indexProduct(id);CoffeeProduct r=adminProductRecords.get(i);adminProductRecords.set(i,new CoffeeProduct(r.productId(),r.name(),r.description(),r.unit(),r.variety(),active,r.createdAt()));}
    @Override public List<WeeklySupplyRecord> adminWeeklySupply(){return List.copyOf(supply);}
    @Override public List<SupplierReview> adminReviews(){return List.copyOf(reviews);}
    @Override public List<SupplierAdvertisement> featuredSupplierAdvertisements(){return List.of(
            new SupplierAdvertisement("Cau Dat Coffee Farm","Highland Arabica, carefully selected","Discover a fresh highland lot for your next roast.","/images/coffee-farm.jpg","Meet supplier"),
            new SupplierAdvertisement("Dak Lak Coffee Co-op","Robusta from the Central Highlands","Plan your next weekly supply with a local producer.","/images/coffee-beans.jpg","Meet supplier"),
            new SupplierAdvertisement("Gia Lai Coffee Supply","Reliable coffee supply for every week","Explore available origins and build a dependable sourcing plan.","/images/coffee-farm.jpg","Meet supplier")
    );}
    @Override public List<SupplierDirectoryEntry> buyerSupplierDirectory(){return supplierNames.keySet().stream().map(id->new SupplierDirectoryEntry(id,supplierNames.get(id),supplierLocations.get(id),supplierScores.get(id),products.stream().filter(product->supplierSupply(id,product).signum()>0).toList())).toList();}
    private long nextShopId(){return adminShops.stream().mapToLong(CoffeeShop::shopId).max().orElse(0)+1;}
    private long nextSupplierId(){return adminSupplierRecords.stream().mapToLong(Supplier::supplierId).max().orElse(0)+1;}
    private int indexShop(long id){for(int i=0;i<adminShops.size();i++)if(adminShops.get(i).shopId()==id)return i;throw new IllegalArgumentException("Coffee shop not found.");}
    private int indexSupplier(long id){for(int i=0;i<adminSupplierRecords.size();i++)if(adminSupplierRecords.get(i).supplierId()==id)return i;throw new IllegalArgumentException("Supplier not found.");}
    private int indexProduct(long id){for(int i=0;i<adminProductRecords.size();i++)if(adminProductRecords.get(i).productId()==id)return i;throw new IllegalArgumentException("Coffee product not found.");}
    private static void requireText(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");}
    private void productsWith(String name){if(!products.contains(name)){List<String> updated=new ArrayList<>(products);updated.add(name);products=List.copyOf(updated);}}
}
