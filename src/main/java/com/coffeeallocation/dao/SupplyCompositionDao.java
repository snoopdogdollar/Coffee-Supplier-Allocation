package com.coffeeallocation.dao;

import com.coffeeallocation.model.SupplyComposition.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SupplyCompositionDao {
    private final Connection connection;
    public SupplyCompositionDao(Connection connection){this.connection=connection;}
    public LocalDateTime databaseTime()throws SQLException {
        try(var statement=connection.prepareStatement("SELECT LOCALTIMESTAMP");var row=statement.executeQuery()){
            if(!row.next())throw new SQLException("Database time was not returned.");return row.getObject(1,LocalDateTime.class);
        }
    }
    public List<Product> products()throws SQLException {
        try(var statement=connection.prepareStatement("SELECT product_id,name,unit FROM coffee_products WHERE is_active ORDER BY unit,product_id");var rows=statement.executeQuery()) {
            List<Product> result=new ArrayList<>();while(rows.next())result.add(new Product(rows.getLong("product_id"),rows.getString("name"),rows.getString("unit")));return List.copyOf(result);
        }
    }
    public List<Contribution> contributions(int year,int week)throws SQLException {
        String sql="""
                SELECT sp.product_id,s.supplier_id,s.name,sw.initial_quantity,sw.remaining_quantity
                FROM supplier_weekly_supply sw
                JOIN supplier_products sp ON sp.supplier_product_id=sw.supplier_product_id AND sp.is_active
                JOIN suppliers s ON s.supplier_id=sp.supplier_id AND s.is_active
                JOIN coffee_products p ON p.product_id=sp.product_id AND p.is_active
                WHERE sw.year=? AND sw.week=? ORDER BY s.supplier_id,sp.product_id
                """;
        try(var statement=connection.prepareStatement(sql)){
            statement.setInt(1,year);statement.setInt(2,week);
            try(var rows=statement.executeQuery()){List<Contribution> result=new ArrayList<>();while(rows.next())result.add(new Contribution(rows.getLong("product_id"),rows.getLong("supplier_id"),rows.getString("name"),rows.getBigDecimal("initial_quantity"),rows.getBigDecimal("remaining_quantity")));return List.copyOf(result);}
        }
    }
}
