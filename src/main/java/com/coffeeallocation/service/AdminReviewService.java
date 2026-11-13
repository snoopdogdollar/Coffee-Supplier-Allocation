package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AdminReviewDao;
import com.coffeeallocation.model.AdminReviewView;
import java.sql.Connection;
import java.sql.SQLException;

public final class AdminReviewService {
    public AdminReviewView.Page page(String supplier, String shop, Long orderId, Long beforeId) throws SQLException {
        String supplierSearch=search(supplier), shopSearch=search(shop);
        if(orderId!=null && orderId<=0) throw new IllegalArgumentException("Order ID must be positive, or leave it blank for all orders.");
        if(beforeId!=null && beforeId<=0) throw new IllegalArgumentException("Review page cursor must be positive.");
        try(var connection=DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try {
                var rows=new AdminReviewDao(connection).page(supplierSearch,shopSearch,orderId,beforeId);
                var visible=rows.subList(0,Math.min(20,rows.size()));
                var page=new AdminReviewView.Page(visible,rows.size()>20?visible.getLast().reviewId():null);
                connection.commit();return page;
            } catch(SQLException|RuntimeException exception) {
                try {connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;
            }
        }
    }
    private static String search(String input) {
        String value=input==null?"":input.strip();
        if(value.indexOf('\0')>=0 || value.codePointCount(0,value.length())>100) {
            throw new IllegalArgumentException("Filters must contain at most 100 characters and no unsupported characters.");
        }
        return value;
    }
}
