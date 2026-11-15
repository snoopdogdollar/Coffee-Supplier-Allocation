package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.DemandExpiryDao;
import com.coffeeallocation.model.DemandExpiryPolicy;
import java.sql.*;

public final class DemandExpiryService {
    public int expireOverdue()throws SQLException{return expire(null);}
    public int expireForShop(long shopId)throws SQLException{if(shopId<=0)throw new IllegalArgumentException("Shop ID must be positive.");return expire(shopId);}
    private int expire(Long shopId)throws SQLException{
        try(var connection=DatabaseConnection.getConnection()){
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);connection.setAutoCommit(false);
            try{var dao=new DemandExpiryDao(connection);int changed=dao.expireBefore(dao.businessDate(),shopId);connection.commit();return changed;}
            catch(SQLException|RuntimeException exception){try{connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;}
        }
    }
    public static void requireActiveWeek(Connection connection,int year,int week)throws SQLException{
        DemandExpiryPolicy.requireActiveWeek(year,week,new DemandExpiryDao(connection).businessDate());
    }
}
