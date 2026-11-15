package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.service.*;
import com.coffeemongers.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SupplierWorkspaceIntegrationTest {
    @Test void supplierBuyerAdminReceiptAndReviewStayConsistent()throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_SUPPLIER_WORKSPACE_INTEGRATION")));
        try(var connection=DatabaseConnection.getConnection();var statement=connection.createStatement()) {
            try(var row=statement.executeQuery("SELECT current_database()")){assertTrue(row.next());assertEquals("coffee_supplier_workspace_test",row.getString(1));}
            statement.execute(Files.readString(Path.of("database/01_schema.sql")));
            statement.execute("""
                    INSERT INTO suppliers(name) VALUES ('Highland Coffee Farm'),('Other Supplier'),('Empty Supplier');
                    INSERT INTO coffee_shops(name) VALUES ('Riverside Coffee'),('Other Shop');
                    INSERT INTO coffee_products(name,unit) VALUES ('Arabica beans','kg'),('Coffee bags','bag'),('Decaf beans','kg');
                    """);
            var ownProducts=new SupplierProductService(1);ownProducts.add(1);ownProducts.add(2);
            var otherProducts=new SupplierProductService(2);otherProducts.add(1);
            var supply=new SupplierSupplyService(1);var otherSupply=new SupplierSupplyService(2);
            LocalDate date=LocalDate.now();int year=date.get(IsoFields.WEEK_BASED_YEAR),week=date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            var catalog=supply.load(date).catalog();long beans=catalog.products().stream().filter(p -> p.productId()==1).findFirst().orElseThrow().supplierProductId();
            long bags=catalog.products().stream().filter(p -> p.productId()==2).findFirst().orElseThrow().supplierProductId();
            long foreignProduct=otherSupply.load(date).catalog().products().getFirst().supplierProductId();
            long beansSupply=supply.save(null,beans,date,new BigDecimal("10.25"),new BigDecimal("100000"));
            supply.save(null,bags,date,new BigDecimal("2"),new BigDecimal("20000"));
            otherSupply.save(null,foreignProduct,date,new BigDecimal("999"),new BigDecimal("990000"));
            var overview=supply.overview();var initial=overview.load(date);
            assertEquals(2,initial.catalog().products().size());assertEquals(2,initial.supply().size());assertEquals(0,initial.totals().orders());
            assertEquals(0,new BigDecimal("10.25").compareTo(initial.supply().stream().filter(r -> r.supplyId()==beansSupply).findFirst().orElseThrow().remainingQuantity()));
            var buyer=new BuyerDemandService(1);long demand=buyer.submit(date,"Please supply for this week",List.of(
                    new BuyerDemandService.Item(1,new BigDecimal("5.25"),"Medium roast"),new BuyerDemandService.Item(2,BigDecimal.ONE,"")));
            assertEquals(0,overview.load(date).totals().orders());
            var confirmation=new AllocationConfirmationService().confirm(year,week,new SharedAllocationService().preview(year,week));
            assertEquals(2,confirmation.orderIds().size());
            var allocated=overview.load(date);assertEquals(2,allocated.totals().pending());assertEquals(2,allocated.orders().size());
            assertTrue(allocated.orders().stream().allMatch(o -> o.summary().supplierId()==1&&o.summary().shopId()==1));
            var beansRow=allocated.supply().stream().filter(r -> r.supplyId()==beansSupply).findFirst().orElseThrow();
            assertEquals(0,new BigDecimal("5.00").compareTo(beansRow.remainingQuantity()));
            assertThrows(IllegalArgumentException.class,() -> supply.save(beansSupply,beans,date,new BigDecimal("4"),BigDecimal.ONE));
            assertThrows(IllegalArgumentException.class,() -> supply.delete(beansSupply));
            assertThrows(IllegalArgumentException.class,() -> otherSupply.save(beansSupply,foreignProduct,date,BigDecimal.TEN,BigDecimal.ONE));
            var orders=new BuyerOrderService(1);long first=confirmation.orderIds().getFirst();orders.complete(first);orders.review(first,true,true,false,"Good beans; delivery was late");
            var after=overview.load(date);assertEquals(1,after.totals().completed());assertEquals(1,after.totals().pending());
            assertTrue(new SupplierOrderService(1).page(null).orders().stream().filter(o -> o.order().id().equals(Long.toString(first))).findFirst().orElseThrow().order().reviewed());
            assertEquals(0,new BigDecimal("5.00").compareTo(supply.load(date).rows().stream().filter(r -> r.id().equals(Long.toString(beansSupply))).findFirst().orElseThrow().remainingQuantity()));
            ownProducts.stopOffering(beans);assertEquals(1,overview.load(date).catalog().products().size());assertEquals(2,overview.load(date).supply().size());
            new BuyerDemandService(2).submit(date,"Other shop",List.of(new BuyerDemandService.Item(1,new BigDecimal("2"),"")));
            var foreign=new AllocationConfirmationService().confirm(year,week,new SharedAllocationService().preview(year,week));assertEquals(1,foreign.orderIds().size());
            assertEquals(1,otherSupply.overview().load(date).totals().orders());assertEquals(2,overview.load(date).totals().orders());
            assertTrue(new SupplierOrderService(1).page(null).orders().stream().noneMatch(o -> foreign.orderIds().contains(Long.parseLong(o.order().id()))));
            List<Long> history=new ArrayList<>();
            try(var insert=connection.prepareStatement("INSERT INTO orders(shop_id,supplier_id,allocation_id,order_date,status) SELECT shop_id,supplier_id,allocation_id,DATE '2020-01-01','COMPLETED' FROM orders CROSS JOIN generate_series(1,23) WHERE order_id=? RETURNING order_id")) {
                insert.setLong(1,first);try(var rows=insert.executeQuery()){while(rows.next())history.add(rows.getLong(1));}
            }
            try(var items=connection.prepareStatement("INSERT INTO order_items(order_id,product_id,quantity,unit_price) SELECT ?,product_id,quantity,unit_price FROM order_items WHERE order_id=?")) {
                for(long id:history){items.setLong(1,id);items.setLong(2,first);items.executeUpdate();}
            }
            var full=overview.load(date);assertEquals(25,full.totals().orders());assertEquals(24,full.totals().completed());assertEquals(5,full.orders().size());
            var page=new SupplierOrderService(1).page(null);assertEquals(20,page.orders().size());
            var last=new SupplierOrderService(1).page(page.nextBeforeId());assertEquals(5,last.orders().size());assertNull(last.nextBeforeId());
            var empty=new SupplierOverviewService(3).load(date);assertTrue(empty.supply().isEmpty());assertTrue(empty.orders().isEmpty());assertEquals(0,empty.totals().orders());
            var anotherWeek=overview.load(date.plusWeeks(1));assertTrue(anotherWeek.supply().isEmpty());assertEquals(25,anotherWeek.totals().orders());
            assertEquals("ALLOCATED",buyer.workspace().detail(demand).demand().status());
        }
    }
}
