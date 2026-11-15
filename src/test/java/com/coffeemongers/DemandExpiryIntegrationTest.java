package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.*;
import com.coffeeallocation.model.*;
import com.coffeeallocation.service.*;
import com.coffeemongers.service.BuyerOrderService;
import com.coffeemongers.service.BuyerWorkspaceService;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DemandExpiryIntegrationTest {
    @Test void expiryClosesOnlyOverdueRemaindersAndPreservesOrders() throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_DEMAND_EXPIRY_INTEGRATION")));
        try(var c=DatabaseConnection.getConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT current_database(),to_regclass('public.demands')")) {
                assertTrue(r.next());assertEquals("coffee_demand_expiry_test",r.getString(1));
                assertNull(r.getString(2),"Use a new empty fixture database.");
            }
            s.execute(Files.readString(Path.of("database/01_schema.sql")));
            s.execute("""
                INSERT INTO coffee_shops(name) VALUES ('Expiry Test Coffee'),('Other Shop');
                INSERT INTO suppliers(name) VALUES ('Highland Farm');
                INSERT INTO coffee_products(name,unit) VALUES ('Arabica','kg'),('Robusta','kg');
                INSERT INTO supplier_products(supplier_id,product_id) VALUES (1,1);
                """);
            LocalDate today=new DemandExpiryDao(c).businessDate(),past=today.minusWeeks(1),future=today.plusWeeks(1);
            long noSupply=demand(c,1,past,"OPEN",true),cancelled=demand(c,1,past,"CANCELLED",true);
            long partial=demand(c,1,past,"OPEN",true),allocated=demand(c,1,past,"ALLOCATED",true);
            long legacyFull=demand(c,1,past,"OPEN",true),other=demand(c,2,past,"OPEN",true);
            long current=demand(c,1,today,"OPEN",true),later=demand(c,1,future,"OPEN",true);
            long empty=demand(c,1,past,"OPEN",false);
            long partialItem=item(c,partial),fullItem=item(c,legacyFull);
            try(var p=c.prepareStatement("INSERT INTO demand_items(demand_id,product_id,quantity) VALUES (?,2,7)")){p.setLong(1,partial);p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO supplier_weekly_supply(supplier_product_id,year,week,initial_quantity,remaining_quantity,unit_price) VALUES (1,?,?,100,80,100)")) {
                p.setInt(1,year(past));p.setInt(2,week(past));p.executeUpdate();
            }
            c.setAutoCommit(false);
            long order=allocate(c,partialItem),fullOrder=allocate(c,fullItem);c.commit();c.setAutoCommit(true);
            var expiry=new DemandExpiryService();assertEquals(2,expiry.expireForShop(1));
            assertEquals("EXPIRED",status(c,noSupply));assertEquals("EXPIRED",status(c,partial));
            assertEquals("CANCELLED",status(c,cancelled));assertEquals("ALLOCATED",status(c,allocated));
            assertEquals("OPEN",status(c,legacyFull));assertEquals("OPEN",status(c,current));
            assertEquals("OPEN",status(c,later));assertEquals("OPEN",status(c,other));assertEquals("OPEN",status(c,empty));
            assertEquals(0,expiry.expireForShop(1));assertEquals(1,expiry.expireOverdue());assertEquals(0,expiry.expireOverdue());
            assertEquals("EXPIRED",status(c,other));
            var detail=new BuyerWorkspaceService(1).detail(partial);
            assertEquals("EXPIRED",detail.demand().status());assertEquals(1,detail.allocations().size());
            assertEquals(0,detail.demand().items().getFirst().remaining().signum());
            assertEquals(0,new BigDecimal("7").compareTo(detail.demand().items().getLast().remaining()));
            assertEquals(4,new BuyerWorkspaceService(1).dashboard().counts().openDemands(),"current, future, fully fulfilled and empty legacy OPEN");
            assertEquals(0,scalar(c,"SELECT remaining_quantity FROM supplier_weekly_supply").compareTo(new BigDecimal("80")));
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM allocations").intValue());
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM orders WHERE status='PENDING'").intValue());
            new BuyerOrderService(1).complete(order);
            new BuyerOrderService(1).review(order,true,false,false,"Received after the delivery week");
            assertEquals("EXPIRED",status(c,partial));assertEquals(1,scalar(c,"SELECT COUNT(*) FROM supplier_reviews").intValue());
            assertEquals(1,scalar(c,"SELECT COUNT(*) FROM orders WHERE status='PENDING'").intValue());

            // Expired demand remains in historical requested totals; cancellation is excluded.
            var chart=new WeeklyMarketService().load(c,today,1L,year(past),week(past));
            assertEquals(0,new BigDecimal("50").compareTo(chart.weeks().getLast().demand()));
            assertThrows(IllegalArgumentException.class,() -> new SharedAllocationService().preview(year(past),week(past)));
            assertThrows(IllegalArgumentException.class,() -> new MatchingService().findForItem(partialItem,year(past),week(past)));
            assertThrows(IllegalArgumentException.class,() -> new AllocationConfirmationService().confirm(year(past),week(past),new BatchAllocationPreview(List.of(),Map.of(),Map.of())));
            BigDecimal before=scalar(c,"SELECT COUNT(*) FROM demands");
            assertThrows(IllegalArgumentException.class,() -> new DemandService().createOpenDemand(1,today,year(past),week(past),"",List.of(new NewDemandItem(1,BigDecimal.ONE,""))));
            assertEquals(before,scalar(c,"SELECT COUNT(*) FROM demands"));assertEquals(2,scalar(c,"SELECT COUNT(*) FROM orders").intValue());
            assertEquals(0,scalar(c,"SELECT remaining_quantity FROM supplier_weekly_supply").compareTo(new BigDecimal("80")));

            // Catch-up is part of the Buyer read, even when the desktop monitor was not running.
            long catchup=demand(c,1,past,"OPEN",true);assertEquals("EXPIRED",new BuyerWorkspaceService(1).detail(catchup).demand().status());
            verifyWaitForConcurrentFulfilment(c,past);
            assertEquals("PENDING",orderStatus(c,fullOrder));

            // Current-week creation and normal allocation still work after lifecycle maintenance.
            long active=new DemandService().createOpenDemand(1,today,year(today),week(today),"Current request",List.of(new NewDemandItem(1,new BigDecimal("3"),"")));
            try(var p=c.prepareStatement("INSERT INTO supplier_weekly_supply(supplier_product_id,year,week,initial_quantity,remaining_quantity,unit_price) VALUES (1,?,?,13,13,100)")) {
                p.setInt(1,year(today));p.setInt(2,week(today));p.executeUpdate();
            }
            var confirmed=new AllocationConfirmationService().confirm(year(today),week(today),new SharedAllocationService().preview(year(today),week(today)));
            assertEquals(2,confirmed.allocatedItems());assertEquals("ALLOCATED",status(c,active));
            assertEquals("ALLOCATED",status(c,current));assertEquals("EXPIRED",status(c,partial));assertEquals("OPEN",status(c,later));
        }
    }
    private void verifyWaitForConcurrentFulfilment(Connection c,LocalDate past)throws Exception {
        long id=demand(c,1,past,"OPEN",true),item=item(c,id);
        try(var holder=DatabaseConnection.getConnection();var worker=DatabaseConnection.getConnection();var executor=Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);worker.setAutoCommit(false);
            worker.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try(var lock=holder.prepareStatement("SELECT demand_id FROM demands WHERE demand_id=? FOR UPDATE")){lock.setLong(1,id);try(var r=lock.executeQuery()){assertTrue(r.next());}}
            int pid;try(var r=worker.createStatement().executeQuery("SELECT pg_backend_pid()")){r.next();pid=r.getInt(1);}
            Future<Integer> result=executor.submit(() -> {int n=new DemandExpiryDao(worker).expireBefore(past.plusWeeks(1),1L);worker.commit();return n;});
            boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline&&!waiting) {
                try(var p=c.prepareStatement("SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=?")){p.setInt(1,pid);try(var r=p.executeQuery()){waiting=r.next()&&r.getBoolean(1);}}
                if(!waiting)Thread.sleep(20);
            }
            if(!waiting){holder.rollback();fail("Expiry did not wait for the demand lock.");}
            // Deliberately leave header OPEN to verify the second statement sees committed allocations.
            allocate(holder,item);holder.commit();assertEquals(0,result.get(10,TimeUnit.SECONDS));
            assertEquals("OPEN",status(c,id));
        }
    }
    private static long demand(Connection c,long shop,LocalDate date,String status,boolean withItem)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO demands(shop_id,required_year,required_week,status) VALUES (?,?,?,?) RETURNING demand_id")) {
            p.setLong(1,shop);p.setInt(2,year(date));p.setInt(3,week(date));p.setString(4,status);
            long id;try(var r=p.executeQuery()){r.next();id=r.getLong(1);}
            if(withItem)try(var line=c.prepareStatement("INSERT INTO demand_items(demand_id,product_id,quantity) VALUES (?,1,10)")){line.setLong(1,id);line.executeUpdate();}
            return id;
        }
    }
    private static long allocate(Connection c,long item)throws SQLException {
        long run=new RecommendationRunDao(c).insert(item,"expiry-test","CONFIRMED");
        long allocation=new AllocationDao(c).insert(item,1,run,1,new BigDecimal("10"),new BigDecimal("100"),"CONFIRMED");
        return new OrderDao(c).insertForAllocation(1,1,allocation,1,new BigDecimal("10"),new BigDecimal("100"),"PENDING");
    }
    private static int year(LocalDate d){return d.get(IsoFields.WEEK_BASED_YEAR);}
    private static int week(LocalDate d){return d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);}
    private static long item(Connection c,long id)throws SQLException {
        try(var p=c.prepareStatement("SELECT demand_item_id FROM demand_items WHERE demand_id=? ORDER BY demand_item_id LIMIT 1")){p.setLong(1,id);try(var r=p.executeQuery()){r.next();return r.getLong(1);}}
    }
    private static String status(Connection c,long id)throws SQLException {return string(c,"SELECT status FROM demands WHERE demand_id=?",id);}
    private static String orderStatus(Connection c,long id)throws SQLException {return string(c,"SELECT status FROM orders WHERE order_id=?",id);}
    private static String string(Connection c,String sql,long id)throws SQLException {try(var p=c.prepareStatement(sql)){p.setLong(1,id);try(var r=p.executeQuery()){r.next();return r.getString(1);}}}
    private static BigDecimal scalar(Connection c,String sql)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getBigDecimal(1);}}
}
