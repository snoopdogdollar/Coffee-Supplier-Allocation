package com.coffeeallocation.dao;

import com.coffeeallocation.model.WeeklyMarketView.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class WeeklyMarketDao {
    private final Connection connection;
    public WeeklyMarketDao(Connection connection) {this.connection=connection;}
    public List<Product> products() throws SQLException {
        try(var statement=connection.prepareStatement("SELECT product_id,name,unit,is_active FROM coffee_products ORDER BY is_active DESC,product_id");
            var rows=statement.executeQuery()) {
            List<Product> result=new ArrayList<>();
            while(rows.next())result.add(new Product(rows.getLong("product_id"),rows.getString("name"),rows.getString("unit"),rows.getBoolean("is_active")));
            return List.copyOf(result);
        }
    }
    public List<Week> weeks(long productId, LocalDate start, LocalDate end) throws SQLException {
        // Aggregate the two sides separately so multiple demand/supply rows never multiply one another.
        String sql="""
                WITH weeks AS (
                    SELECT d::date AS monday, EXTRACT(ISOYEAR FROM d)::int AS year,
                           EXTRACT(WEEK FROM d)::int AS week
                    FROM generate_series(?::date, ?::date, INTERVAL '1 week') d
                ), supply AS (
                    SELECT sw.year,sw.week,SUM(sw.initial_quantity) AS quantity
                    FROM supplier_weekly_supply sw
                    JOIN supplier_products sp ON sp.supplier_product_id=sw.supplier_product_id
                    JOIN weeks w ON w.year=sw.year AND w.week=sw.week
                    WHERE sp.product_id=?
                    GROUP BY sw.year,sw.week
                ), demand AS (
                    SELECT d.required_year AS year,d.required_week AS week,SUM(di.quantity) AS quantity
                    FROM demands d JOIN demand_items di ON di.demand_id=d.demand_id
                    JOIN weeks w ON w.year=d.required_year AND w.week=d.required_week
                    WHERE di.product_id=? AND d.status<>'CANCELLED'
                    GROUP BY d.required_year,d.required_week
                )
                SELECT w.monday,COALESCE(s.quantity,0) AS supply,COALESCE(d.quantity,0) AS demand
                FROM weeks w
                LEFT JOIN supply s ON s.year=w.year AND s.week=w.week
                LEFT JOIN demand d ON d.year=w.year AND d.week=w.week
                ORDER BY w.monday
                """;
        try(var statement=connection.prepareStatement(sql)) {
            statement.setObject(1,start);statement.setObject(2,end);
            statement.setLong(3,productId);statement.setLong(4,productId);
            try(var rows=statement.executeQuery()) {
                List<Week> result=new ArrayList<>();
                while(rows.next())result.add(new Week(rows.getObject("monday",LocalDate.class),rows.getBigDecimal("supply"),rows.getBigDecimal("demand")));
                return List.copyOf(result);
            }
        }
    }
}
