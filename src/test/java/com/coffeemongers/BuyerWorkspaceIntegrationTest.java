package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.model.NewDemandItem;
import com.coffeeallocation.service.*;
import com.coffeemongers.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BuyerWorkspaceIntegrationTest {
    @Test void buyerReadsAreScopedAndFollowAdminAllocationReceiptAndReview() throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_BUYER_WORKSPACE_INTEGRATION")));
        try(var connection=DatabaseConnection.getConnection();var statement=connection.createStatement()) {
            try(var row=statement.executeQuery("SELECT current_database()")){assertTrue(row.next());assertEquals("coffee_buyer_workspace_test",row.getString(1));}
            statement.execute(Files.readString(Path.of("database/01_schema.sql")));
            statement.execute("""
                    INSERT INTO coffee_shops(name,address) VALUES ('Riverside Coffee','12 River Street'),('Other Shop','Other address'),('Empty Shop','');
                    INSERT INTO suppliers(name) VALUES ('Highland Coffee Farm'),('Valley Coffee Co-op');
                    INSERT INTO coffee_products(name,unit) VALUES ('Arabica beans','kg'),('Coffee bags','bag');
                    INSERT INTO supplier_products(supplier_id,product_id) VALUES (1,1),(2,1);
                    INSERT INTO demands(shop_id,required_year,required_week,status)
                      SELECT 1,2025,1,'CANCELLED' FROM generate_series(1,23);
                    INSERT INTO demand_items(demand_id,product_id,quantity) SELECT demand_id,1,1 FROM demands;
                    """);
            var empty=new BuyerWorkspaceService(3).dashboard();assertEquals(0,empty.counts().demands());assertTrue(empty.demands().isEmpty());
            LocalDate delivery=LocalDate.now().plusWeeks(2);int year=delivery.get(IsoFields.WEEK_BASED_YEAR),week=delivery.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            LocalDate otherWeek=delivery.plusWeeks(1);
            long foreign=new DemandService().createOpenDemand(2,LocalDate.now(),otherWeek.get(IsoFields.WEEK_BASED_YEAR),otherWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),"Other shop only",List.of(new NewDemandItem(1,BigDecimal.ONE,"")));
            var demands=new BuyerDemandService(1);var workspace=demands.workspace();
            long id=demands.submit(delivery,"Morning roast; keep products separate",List.of(new BuyerDemandService.Item(1,new BigDecimal("10.25"),"Medium roast"),new BuyerDemandService.Item(2,new BigDecimal("2"),"")));
            var before=workspace.dashboard();assertEquals(24,before.counts().demands());assertEquals(1,before.counts().openDemands());assertEquals(0,before.counts().orders());assertEquals(5,before.demands().size());
            assertThrows(IllegalArgumentException.class,() -> workspace.detail(foreign));
            assertThrows(IllegalArgumentException.class,() -> new BuyerWorkspaceService(2).detail(id));
            var page1=workspace.demands(null);var page2=workspace.demands(page1.nextBeforeId());
            assertEquals(20,page1.demands().size());assertEquals(4,page2.demands().size());assertNull(page2.nextBeforeId());
            assertTrue(page1.demands().stream().noneMatch(a -> page2.demands().stream().anyMatch(b -> a.id()==b.id())));
            assertEquals(new BigDecimal("10.25"),workspace.detail(id).demand().items().getFirst().remaining());
            try(var supply=connection.prepareStatement("INSERT INTO supplier_weekly_supply(supplier_product_id,year,week,initial_quantity,remaining_quantity,unit_price) VALUES (?, ?, ?, ?, ?, ?)")) {
                for(int i=1;i<=2;i++) {supply.setInt(1,i);supply.setInt(2,year);supply.setInt(3,week);supply.setBigDecimal(4,new BigDecimal(i==1?"6.00":"4.25"));supply.setBigDecimal(5,new BigDecimal(i==1?"6.00":"4.25"));supply.setBigDecimal(6,new BigDecimal(i==1?"100000":"120000"));supply.executeUpdate();}
            }
            var confirmation=new AllocationConfirmationService().confirm(year,week,new SharedAllocationService().preview(year,week));
            assertEquals(2,confirmation.orderIds().size());
            var detail=workspace.detail(id);assertEquals("OPEN",detail.demand().status());assertEquals(2,detail.allocations().size());
            assertEquals(0,new BigDecimal("10.25").compareTo(detail.demand().items().getFirst().allocated()));
            assertEquals(0,detail.demand().items().getFirst().remaining().signum());
            assertEquals(0,new BigDecimal("2").compareTo(detail.demand().items().getLast().remaining()));
            assertEquals("1 / 2 lines allocated",detail.demand().progress());
            long order=confirmation.orderIds().getFirst();
            try(var duplicate=connection.prepareStatement("INSERT INTO orders(shop_id,supplier_id,allocation_id,status) SELECT shop_id,supplier_id,allocation_id,'PENDING' FROM orders WHERE order_id=?")){duplicate.setLong(1,order);duplicate.executeUpdate();}
            assertEquals(0,new BigDecimal("10.25").compareTo(workspace.detail(id).demand().items().getFirst().allocated()),"orders cannot multiply allocation quantities");
            var buyerOrders=new BuyerOrderService(1);assertThrows(IllegalArgumentException.class,() -> new BuyerOrderService(2).complete(order));
            buyerOrders.complete(order);assertEquals(1,workspace.dashboard().counts().reviewsDue());
            buyerOrders.review(order,true,false,true,"Good beans; quantity discrepancy");
            assertEquals(0,workspace.dashboard().counts().reviewsDue());assertEquals(2,workspace.dashboard().counts().pendingOrders());
            assertEquals(3,workspace.dashboard().counts().orders());
            assertTrue(buyerOrders.page(null).orders().stream().filter(o -> o.id().equals(Long.toString(order))).findFirst().orElseThrow().reviewed());
            statement.execute("UPDATE suppliers SET is_active=false WHERE supplier_id=1");
            assertEquals(2,workspace.detail(id).allocations().size());
        }
    }
}
