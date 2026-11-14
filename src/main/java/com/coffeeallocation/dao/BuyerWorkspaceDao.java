package com.coffeeallocation.dao;

import com.coffeeallocation.model.BuyerWorkspaceView.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Read-only queries; every demand and allocation read is scoped to its owning shop. */
public final class BuyerWorkspaceDao {
    private final Connection connection;
    public BuyerWorkspaceDao(Connection connection) {this.connection=connection;}
    public Shop shop(long shopId) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT shop_id,name,address FROM coffee_shops WHERE shop_id=?")) {
            statement.setLong(1,shopId);
            try(var rows=statement.executeQuery()) {
                if(!rows.next())throw new IllegalArgumentException("Your linked coffee shop is unavailable.");
                return new Shop(rows.getLong("shop_id"),rows.getString("name"),rows.getString("address"));
            }
        }
    }
    public Counts counts(long shopId) throws SQLException {
        String sql="""
                SELECT (SELECT COUNT(*) FROM demands WHERE shop_id=?) AS demands,
                  (SELECT COUNT(*) FROM demands WHERE shop_id=? AND status='OPEN') AS open_demands,
                  (SELECT COUNT(*) FROM orders WHERE shop_id=?) AS orders,
                  (SELECT COUNT(*) FROM orders WHERE shop_id=? AND status='PENDING') AS pending_orders,
                  (SELECT COUNT(*) FROM orders o WHERE shop_id=? AND status='COMPLETED'
                    AND NOT EXISTS (SELECT 1 FROM supplier_reviews r WHERE r.order_id=o.order_id
                      AND r.supplier_id=o.supplier_id)) AS reviews_due
                """;
        try(var statement=connection.prepareStatement(sql)) {
            for(int i=1;i<=5;i++)statement.setLong(i,shopId);
            try(var row=statement.executeQuery()) {
                if(!row.next())throw new SQLException("Buyer totals were not returned.");
                return new Counts(row.getLong("demands"),row.getLong("open_demands"),row.getLong("orders"),
                        row.getLong("pending_orders"),row.getLong("reviews_due"));
            }
        }
    }
    public List<Demand> demands(long shopId,Long beforeId,Long demandId) throws SQLException {
        String sql="""
                WITH page AS (
                  SELECT demand_id,request_date,required_year,required_week,status,note
                  FROM demands WHERE shop_id=? AND (?::bigint IS NULL OR demand_id<?)
                    AND (?::bigint IS NULL OR demand_id=?) ORDER BY demand_id DESC LIMIT 21
                )
                SELECT page.*,di.demand_item_id,di.product_id,p.name AS product_name,p.unit,
                  di.quantity,di.note AS item_note,COALESCE(committed.quantity,0) AS allocated
                FROM page LEFT JOIN demand_items di ON di.demand_id=page.demand_id
                LEFT JOIN coffee_products p ON p.product_id=di.product_id
                LEFT JOIN LATERAL (
                  SELECT SUM(a.allocated_quantity) AS quantity FROM allocations a
                  WHERE a.demand_item_id=di.demand_item_id AND a.status='CONFIRMED'
                ) committed ON true
                ORDER BY page.demand_id DESC,di.demand_item_id
                """;
        Map<Long,Demand> result=new LinkedHashMap<>();
        Map<Long,List<Line>> lines=new HashMap<>();
        try(var statement=connection.prepareStatement(sql)) {
            statement.setLong(1,shopId);statement.setObject(2,beforeId,Types.BIGINT);statement.setObject(3,beforeId,Types.BIGINT);
            statement.setObject(4,demandId,Types.BIGINT);statement.setObject(5,demandId,Types.BIGINT);
            try(var rows=statement.executeQuery()) {
                while(rows.next()) {
                    long id=rows.getLong("demand_id");
                    if(!result.containsKey(id))result.put(id,new Demand(id,rows.getObject("request_date",LocalDate.class),
                            rows.getInt("required_year"),rows.getInt("required_week"),rows.getString("status"),
                            rows.getString("note"),List.of()));
                    if(rows.getObject("demand_item_id")!=null) {
                    lines.computeIfAbsent(rows.getLong("demand_id"),ignored -> new ArrayList<>()).add(new Line(
                            rows.getLong("demand_item_id"),rows.getLong("product_id"),rows.getString("product_name"),
                            rows.getString("unit"),rows.getBigDecimal("quantity"),rows.getBigDecimal("allocated"),rows.getString("item_note")));
                    }
                }
            }
        }
        return result.values().stream().map(d -> new Demand(d.id(),d.requestDate(),d.year(),d.week(),d.status(),d.note(),
                lines.getOrDefault(d.id(),List.of()))).toList();
    }
    public List<Allocation> allocations(long shopId,long demandId) throws SQLException {
        String sql="""
                SELECT a.allocation_id,a.demand_item_id,p.name AS product_name,p.unit,
                       a.supplier_id,s.name AS supplier_name,a.allocated_quantity,a.allocated_price
                FROM allocations a JOIN demand_items di ON di.demand_item_id=a.demand_item_id
                JOIN demands d ON d.demand_id=di.demand_id
                JOIN coffee_products p ON p.product_id=di.product_id JOIN suppliers s ON s.supplier_id=a.supplier_id
                WHERE d.shop_id=? AND d.demand_id=? AND a.status='CONFIRMED'
                ORDER BY a.demand_item_id,a.allocation_id
                """;
        try(var statement=connection.prepareStatement(sql)) {
            statement.setLong(1,shopId);statement.setLong(2,demandId);
            try(var rows=statement.executeQuery()) {
                List<Allocation> result=new ArrayList<>();
                while(rows.next())result.add(new Allocation(rows.getLong("allocation_id"),rows.getLong("demand_item_id"),
                        rows.getString("product_name"),rows.getString("unit"),rows.getLong("supplier_id"),rows.getString("supplier_name"),
                        rows.getBigDecimal("allocated_quantity"),rows.getBigDecimal("allocated_price")));
                return List.copyOf(result);
            }
        }
    }
}
