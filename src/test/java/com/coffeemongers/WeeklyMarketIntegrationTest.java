package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.service.AdminOverviewService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt in only against a new empty disposable database. */
class WeeklyMarketIntegrationTest {
    @Test void savedWeeklyTotalsStaySeparateAndRespectProductAndIsoWeek() throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_CHART_INTEGRATION")));
        try(var c=DatabaseConnection.getConnection();var statement=c.createStatement()) {
            try(var rows=statement.executeQuery("SELECT current_database()")) {
                assertTrue(rows.next());assertEquals("coffee_weekly_chart_test",rows.getString(1));
            }
            statement.execute(Files.readString(Path.of("database/01_schema.sql")));
            var service=new AdminOverviewService();
            assertNull(service.load(null,2021,1).weeklyMarket().product());
            statement.execute("""
                    INSERT INTO coffee_shops(name,is_active) VALUES ('Active shop',true),('Inactive shop',false);
                    INSERT INTO suppliers(name,is_active) VALUES ('Active supplier',true),('Inactive supplier',false);
                    INSERT INTO coffee_products(name,unit) VALUES ('Coffee kg','kg'),('Coffee bags','bag');
                    INSERT INTO supplier_products(supplier_id,product_id,is_active) VALUES (1,1,true),(2,1,false),(1,2,true);
                    INSERT INTO supplier_weekly_supply(supplier_product_id,year,week,initial_quantity,remaining_quantity,unit_price) VALUES
                      (1,2020,53,10.25,2,1),(2,2020,53,20.50,3,1),(1,2021,1,7,7,1),(3,2020,53,1000,1000,1);
                    INSERT INTO demands(shop_id,required_year,required_week,status,request_date) VALUES
                      (1,2020,53,'OPEN','2019-01-01'),(2,2020,53,'ALLOCATED','2019-01-01'),
                      (1,2020,53,'CANCELLED','2019-01-01'),(1,2021,1,'OPEN','2019-01-01'),
                      (1,2020,53,'OPEN','2019-01-01');
                    INSERT INTO demand_items(demand_id,product_id,quantity) VALUES
                      (1,1,5.25),(1,1,4.75),(2,1,11),(3,1,999),(4,1,8),(5,2,2000);
                    """);
            var chart=service.load(1L,2021,1).weeklyMarket();
            assertEquals(8,chart.weeks().size());assertEquals("kg",chart.product().unit());
            var oldWeek=chart.weeks().get(6);var newWeek=chart.weeks().getLast();
            assertEquals("2020-W53",oldWeek.label());assertEquals("2021-W01",newWeek.label());
            assertEquals(0,new BigDecimal("30.75").compareTo(oldWeek.supply()));
            assertEquals(0,new BigDecimal("21").compareTo(oldWeek.demand()));
            assertEquals(0,new BigDecimal("7").compareTo(newWeek.supply()));
            assertEquals(0,new BigDecimal("8").compareTo(newWeek.demand()));
            assertTrue(chart.weeks().subList(0,6).stream().allMatch(w -> w.supply().signum()==0&&w.demand().signum()==0));
            var bags=service.load(2L,2021,1).weeklyMarket();
            assertEquals("bag",bags.product().unit());
            assertEquals(0,new BigDecimal("1000").compareTo(bags.weeks().get(6).supply()));
            assertEquals(0,new BigDecimal("2000").compareTo(bags.weeks().get(6).demand()));
            assertThrows(IllegalArgumentException.class,() -> service.load(999L,2021,1));
            statement.execute("UPDATE supplier_weekly_supply SET remaining_quantity=1 WHERE supplier_product_id=1 AND year=2020");
            assertEquals(0,new BigDecimal("30.75").compareTo(service.load(1L,2021,1).weeklyMarket().weeks().get(6).supply()));
        }
    }
}
