package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.SupplierHistoryDao;
import com.coffeeallocation.service.AdminOverviewService;
import com.coffeeallocation.service.SupplierRankingService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in: use only a new, disposable database named coffee_ranking_test. */
class SupplierRankingIntegrationTest {
    @Test void postgresEligibilityAndOverviewSnapshot() throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_RANKING_INTEGRATION")));
        try (var connection=DatabaseConnection.getConnection()) {
            try(var statement=connection.createStatement();var row=statement.executeQuery("SELECT current_database()")) {
                assertTrue(row.next());assertEquals("coffee_ranking_test",row.getString(1),"Use a disposable test database");
            }
            try(var statement=connection.createStatement()) {
                statement.execute(Files.readString(Path.of("database/01_schema.sql")));
            }
            var empty=new AdminOverviewService().load();
            assertTrue(empty.rankings().suppliers().isEmpty());assertTrue(empty.rankings().topSuppliers().isEmpty());
            execute(connection,"INSERT INTO coffee_shops(name) VALUES ('Ranking Shop')");
            execute(connection,"INSERT INTO coffee_products(name,unit) VALUES ('Test coffee','kg')");
            execute(connection,"INSERT INTO demands(shop_id,required_week,required_year,status) VALUES (1,46,2026,'ALLOCATED')");
            execute(connection,"INSERT INTO demand_items(demand_id,product_id,quantity) VALUES (1,1,1000)");
            long good=supplier(connection,"Good",true), inactive=supplier(connection,"Inactive",false),
                    limited=supplier(connection,"Limited",true), fresh=supplier(connection,"No reviews",true),
                    tie=supplier(connection,"Tie with more reviews",true);
            for(int i=0;i<10;i++) {
                long order=order(connection,good,"COMPLETED");
                review(connection,order,good,i<8,i<9,i<7,"2001-01-01 12:00:00");
                if(i==0)review(connection,order,good,false,false,false,"2002-01-01 12:00:00");
            }
            for(int i=0;i<5;i++)review(connection,order(connection,inactive,"COMPLETED"),inactive,true,true,true,"2001-01-02 12:00:00");
            for(int i=0;i<4;i++)review(connection,order(connection,limited,"COMPLETED"),limited,true,true,true,"2001-01-03 12:00:00");
            for(int i=0;i<20;i++)review(connection,order(connection,tie,"COMPLETED"),tie,i<16,i<18,i<14,"2001-01-04 12:00:00");
            review(connection,order(connection,good,"PENDING"),good,true,true,true,"2001-01-01 12:00:00");
            review(connection,order(connection,good,"CANCELLED"),good,true,true,true,"2001-01-01 12:00:00");
            review(connection,order(connection,good,"COMPLETED"),good,true,true,true,"1999-01-01 12:00:00");
            review(connection,order(connection,good,"COMPLETED"),good,true,true,true,"2099-01-01 12:00:00");
            var snapshot=new AdminOverviewService().load();
            assertEquals(5,snapshot.rankings().suppliers().size());
            assertEquals(List.of(tie,good),snapshot.rankings().topSuppliers().stream().map(e -> e.history().supplierId()).toList());
            var goodRow=snapshot.rankings().suppliers().stream().filter(e -> e.history().supplierId()==good).findFirst().orElseThrow();
            assertEquals(10,goodRow.history().reviewCount());assertEquals(new BigDecimal("80.00"),goodRow.score());
            assertEquals(new BigDecimal("80.00"),goodRow.qualityRate());assertEquals(new BigDecimal("90.00"),goodRow.quantityRate());
            assertEquals(new BigDecimal("70.00"),goodRow.onTimeRate());
            assertEquals(LocalDateTime.of(2001,1,1,12,0),goodRow.history().latestReviewAt());
            var newRow=snapshot.rankings().suppliers().stream().filter(e -> e.history().supplierId()==fresh).findFirst().orElseThrow();
            assertNull(newRow.score());assertNull(newRow.history().latestReviewAt());
            var historical=new SupplierRankingService().rank(new SupplierHistoryDao(connection)
                    .findSummaries(LocalDateTime.of(2000,12,31,23,59)));
            assertTrue(historical.suppliers().stream().allMatch(e -> e.score()==null));
            execute(connection,"UPDATE suppliers SET is_active=false WHERE supplier_id="+good);
            var refreshed=new AdminOverviewService().load();
            assertEquals(List.of(tie),refreshed.rankings().topSuppliers().stream().map(e -> e.history().supplierId()).toList());
            assertEquals(5,refreshed.rankings().suppliers().size());
        }
    }
    private static long supplier(Connection c,String name,boolean active) throws SQLException {
        long id=insert(c,"INSERT INTO suppliers(name,is_active) VALUES (?,?) RETURNING supplier_id",name,active);
        long product=insert(c,"INSERT INTO supplier_products(supplier_id,product_id) VALUES (?,1) RETURNING supplier_product_id",id);
        long supply=insert(c,"INSERT INTO supplier_weekly_supply(supplier_product_id,week,year,initial_quantity,remaining_quantity,unit_price) VALUES (?,46,2026,1000,1000,1) RETURNING supply_id",product);
        long run=insert(c,"INSERT INTO recommendation_runs(demand_item_id,algorithm_version,status) VALUES (1,'ranking-test','CONFIRMED') RETURNING run_id");
        insert(c,"INSERT INTO allocations(demand_item_id,supplier_id,run_id,supply_id,allocated_quantity,allocated_price,status) VALUES (1,?,?,?,100,1,'CONFIRMED') RETURNING allocation_id",id,run,supply);
        return id;
    }
    private static long order(Connection c,long supplier,String status) throws SQLException {
        return insert(c,"INSERT INTO orders(shop_id,supplier_id,allocation_id,status,created_at) SELECT 1,?,allocation_id,?,TIMESTAMP '2000-01-01 12:00:00' FROM allocations WHERE supplier_id=? RETURNING order_id",supplier,status,supplier);
    }
    private static void review(Connection c,long order,long supplier,boolean quality,boolean quantity,boolean onTime,String time) throws SQLException {
        insert(c,"INSERT INTO supplier_reviews(order_id,supplier_id,quality_ok,quantity_ok,on_time_ok,created_at) VALUES (?,?,?,?,?,?::timestamp) RETURNING review_id",order,supplier,quality,quantity,onTime,time);
    }
    private static long insert(Connection c,String sql,Object... values) throws SQLException {
        try(var statement=c.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);
            try(var row=statement.executeQuery()) {assertTrue(row.next());return row.getLong(1);}
        }
    }
    private static void execute(Connection c,String sql) throws SQLException {
        try(var statement=c.createStatement()) {statement.execute(sql);}
    }
}
