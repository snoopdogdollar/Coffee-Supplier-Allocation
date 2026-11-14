package com.coffeemongers.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.BuyerWorkspaceDao;
import com.coffeeallocation.dao.OrderQueryDao;
import com.coffeeallocation.model.BuyerWorkspaceView.*;
import com.coffeemongers.model.OrderRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class BuyerWorkspaceService {
    private final long shopId;
    public BuyerWorkspaceService(long shopId) {
        if(shopId<=0)throw new IllegalArgumentException("Shop ID must be positive.");this.shopId=shopId;
    }
    public record Dashboard(Shop shop,Counts counts,List<Demand> demands,List<OrderRecord> orders) {
        public Dashboard {demands=List.copyOf(demands);orders=List.copyOf(orders);}
    }
    public Dashboard dashboard() throws SQLException {
        return read((connection,dao) -> new Dashboard(dao.shop(shopId),dao.counts(shopId),dao.demands(shopId,null,null).stream().limit(5).toList(),
                new OrderQueryDao(connection).findBuyerPage(shopId,null).stream().limit(5).map(BuyerOrderService::toRecord).toList()));
    }
    public Page demands(Long beforeId) throws SQLException {
        if(beforeId!=null&&beforeId<=0)throw new IllegalArgumentException("Demand cursor must be positive.");
        return read((connection,dao) -> {
            var rows=dao.demands(shopId,beforeId,null);var visible=rows.subList(0,Math.min(20,rows.size()));
            return new Page(visible,rows.size()>20?visible.getLast().id():null);
        });
    }
    public Detail detail(long demandId) throws SQLException {
        if(demandId<=0)throw new IllegalArgumentException("Demand ID must be positive.");
        return read((connection,dao) -> {
            var demands=dao.demands(shopId,null,demandId);
            if(demands.isEmpty())throw new IllegalArgumentException("This demand is not available for your shop.");
            return new Detail(dao.shop(shopId),demands.getFirst(),dao.allocations(shopId,demandId));
        });
    }
    private <T>T read(Read<T> action) throws SQLException {
        try(var connection=DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try {T result=action.run(connection,new BuyerWorkspaceDao(connection));connection.commit();return result;}
            catch(SQLException|RuntimeException exception){try{connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;}
        }
    }
    @FunctionalInterface private interface Read<T>{T run(Connection connection,BuyerWorkspaceDao dao)throws SQLException;}
}
