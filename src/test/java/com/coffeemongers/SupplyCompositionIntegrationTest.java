package com.coffeemongers;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.model.SupplyComposition.*;
import com.coffeeallocation.service.*;
import com.coffeemongers.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SupplyCompositionIntegrationTest {
    @Test void categoriesAndStacksFollowAllocationAndActiveCatalog()throws Exception{
        assumeTrue("true".equals(System.getenv("COFFEE_COMPOSITION_INTEGRATION")));
        try(var c=DatabaseConnection.getConnection();var statement=c.createStatement()){
            try(var row=statement.executeQuery("SELECT current_database()")){assertTrue(row.next());assertEquals("coffee_composition_test",row.getString(1));}
            statement.execute(Files.readString(Path.of("database/01_schema.sql")));
            var service=new SupplyCompositionService();assertTrue(service.load(null).products().isEmpty());
            statement.execute("""
                    INSERT INTO coffee_shops(name) VALUES ('Chart Shop');
                    INSERT INTO suppliers(name,is_active) VALUES ('Highland Farm',true),('Valley Co-op',true),('Inactive Supplier',false);
                    INSERT INTO coffee_products(name,unit) VALUES ('Arabica','kg'),('Robusta','kg'),('Espresso Blend','kg'),('Decaf','kg'),('Honey Process','kg'),('Washed Coffee','kg');
                    INSERT INTO supplier_products(supplier_id,product_id,is_active) VALUES (1,1,true),(2,1,true),(3,1,true),(1,2,true),(2,2,false),(1,3,true);
                    """);
            LocalDate date=LocalDate.now();int year=date.get(IsoFields.WEEK_BASED_YEAR),week=date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            try(var supply=c.prepareStatement("INSERT INTO supplier_weekly_supply(supplier_product_id,year,week,initial_quantity,remaining_quantity,unit_price) VALUES (?,?,?,?,?,?)")){
                int[] links={1,2,3,4,5};int[] amounts={10,20,999,30,100};
                for(int i=0;i<links.length;i++){supply.setInt(1,links[i]);supply.setInt(2,year);supply.setInt(3,week);supply.setInt(4,amounts[i]);supply.setInt(5,amounts[i]);supply.setInt(6,100+i*100);supply.executeUpdate();}
                LocalDate other=date.plusWeeks(1);supply.setInt(1,6);supply.setInt(2,other.get(IsoFields.WEEK_BASED_YEAR));supply.setInt(3,other.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));supply.setInt(4,500);supply.setInt(5,500);supply.setInt(6,100);supply.executeUpdate();
            }
            var initial=service.load(date);assertEquals(6,initial.products().size());assertEquals(3,initial.contributions().size());assertEquals(0,new BigDecimal("30").compareTo(initial.total(1,Measure.REMAINING)));assertEquals(0,new BigDecimal("30").compareTo(initial.total(2,Measure.REMAINING)));assertEquals(BigDecimal.ZERO,initial.total(3,Measure.REMAINING));
            new BuyerDemandService(1).submit(date,"Chart allocation",List.of(new BuyerDemandService.Item(1,new BigDecimal("3.25"),"")));
            new AllocationConfirmationService().confirm(year,week,new SharedAllocationService().preview(year,week));
            var after=service.load(date);assertEquals(0,new BigDecimal("26.75").compareTo(after.total(1,Measure.REMAINING)));assertEquals(0,new BigDecimal("30").compareTo(after.total(1,Measure.DECLARED)));assertEquals(0,new BigDecimal("6.75").compareTo(after.amount(1,1,Measure.REMAINING)));
            var admin=new AdminCoffeeProductService();long added=admin.add("Capsules","","bag","");var withNew=service.load(date);assertEquals(7,withNew.products().size());assertEquals(2,withNew.units().size());assertEquals(BigDecimal.ZERO,withNew.total(added,Measure.REMAINING));admin.changeStatus(added,true);assertEquals(6,service.load(date).products().size());
            admin.changeStatus(1,true);assertEquals(5,service.load(date).products().size());assertTrue(service.load(date).contributions().stream().noneMatch(r -> r.productId()==1));admin.changeStatus(1,false);
            statement.execute("UPDATE suppliers SET is_active=false WHERE supplier_id=1");assertEquals(0,new BigDecimal("20").compareTo(service.load(date).total(1,Measure.REMAINING)));statement.execute("UPDATE suppliers SET is_active=true WHERE supplier_id=1");
            statement.execute("UPDATE supplier_products SET is_active=false WHERE supplier_product_id=1");assertEquals(0,new BigDecimal("20").compareTo(service.load(date).total(1,Measure.REMAINING)));statement.execute("UPDATE supplier_products SET is_active=true WHERE supplier_product_id=1");
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM supplier_weekly_supply")){assertTrue(rows.next());assertEquals(6,rows.getInt(1));}
        }
    }
}
