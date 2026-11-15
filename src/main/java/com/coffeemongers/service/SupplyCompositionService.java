package com.coffeemongers.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.SupplyCompositionDao;
import com.coffeeallocation.model.SupplyComposition.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;

public final class SupplyCompositionService {
    public Snapshot load(LocalDate requestedWeek)throws SQLException {
        if(requestedWeek!=null)SupplierOverviewService.monday(requestedWeek);
        try(var connection=DatabaseConnection.getConnection()){
            connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try{
                var dao=new SupplyCompositionDao(connection);var now=dao.databaseTime();var week=SupplierOverviewService.monday(requestedWeek==null?now.toLocalDate():requestedWeek);
                var result=new Snapshot(week,now,dao.products(),dao.contributions(week.get(IsoFields.WEEK_BASED_YEAR),week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
                connection.commit();return result;
            }catch(SQLException|RuntimeException exception){try{connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;}
        }
    }
    public Snapshot demo(MarketplaceService market,LocalDate selectedWeek){
        var week=SupplierOverviewService.monday(selectedWeek==null?LocalDate.now():selectedWeek);
        var products=market.adminCoffeeProducts().stream().filter(p -> p.isActive()).map(p -> new Product(p.productId(),p.name(),p.unit())).toList();
        Map<Long,String> suppliers=new LinkedHashMap<>();market.adminSuppliers().stream().filter(s -> s.isActive()).forEach(s -> suppliers.put(s.supplierId(),s.name()));
        List<Contribution> contributions=new ArrayList<>();
        for(var row:market.adminWeeklySupply())if(row.weekStart().equals(week)){
            long supplierId;try{supplierId=Long.parseLong(row.supplierId().replaceFirst("^SUP-",""));}catch(NumberFormatException exception){continue;}
            if(!suppliers.containsKey(supplierId))continue;
            products.stream().filter(p -> p.name().equals(row.productName())&&p.unit().equals(row.unit())).findFirst().ifPresent(p -> contributions.add(new Contribution(p.id(),supplierId,suppliers.get(supplierId),row.initialQuantity(),row.remainingQuantity())));
        }
        return new Snapshot(week,LocalDateTime.now(),products,contributions);
    }
}
