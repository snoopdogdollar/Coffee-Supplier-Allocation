package com.coffeeallocation.dao;

import com.coffeeallocation.model.DemandExpiryPolicy;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

public final class DemandExpiryDao {
    private final Connection connection;
    public DemandExpiryDao(Connection connection){this.connection=connection;}
    public LocalDate businessDate()throws SQLException{
        try(var statement=connection.prepareStatement("SELECT (clock_timestamp() AT TIME ZONE ?)::date")){
            statement.setString(1,DemandExpiryPolicy.BUSINESS_ZONE.getId());
            try(var row=statement.executeQuery()){if(!row.next())throw new SQLException("Business date was not returned.");return row.getObject(1,LocalDate.class);}
        }
    }
    /** Caller owns a read-write transaction. Lock demands in the same order as confirmation. */
    public int expireBefore(LocalDate date,Long shopId)throws SQLException{
        if(connection.getAutoCommit()||connection.isReadOnly())throw new SQLException("Expiry requires a read-write transaction.");
        int year=date.get(IsoFields.WEEK_BASED_YEAR),week=date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        List<Long> locked=new ArrayList<>();
        try(var statement=connection.prepareStatement("""
                SELECT demand_id FROM demands
                WHERE status='OPEN' AND (required_year,required_week)<(?,?)
                  AND (?::bigint IS NULL OR shop_id=?)
                ORDER BY demand_id FOR UPDATE
                """)){
            statement.setInt(1,year);statement.setInt(2,week);statement.setObject(3,shopId,Types.BIGINT);statement.setObject(4,shopId,Types.BIGINT);
            try(var rows=statement.executeQuery()){while(rows.next())locked.add(rows.getLong(1));}
        }
        if(locked.isEmpty())return 0;
        // A new READ COMMITTED statement sees confirmations that committed while locks were awaited.
        Array ids=connection.createArrayOf("bigint",locked.toArray(Long[]::new));
        try(var statement=connection.prepareStatement("""
                UPDATE demands d SET status='EXPIRED'
                WHERE d.demand_id=ANY(?) AND d.status='OPEN'
                  AND (d.required_year,d.required_week)<(?,?)
                  AND EXISTS (
                    SELECT 1 FROM demand_items di WHERE di.demand_id=d.demand_id
                      AND di.quantity>COALESCE((SELECT SUM(a.allocated_quantity) FROM allocations a
                        WHERE a.demand_item_id=di.demand_item_id AND a.status='CONFIRMED'),0)
                  )
                """)){
            statement.setArray(1,ids);statement.setInt(2,year);statement.setInt(3,week);return statement.executeUpdate();
        }finally{ids.free();}
    }
}
