package com.coffeeallocation.dao;

import com.coffeeallocation.model.CoffeeShop;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CoffeeShopDao {

    private final Connection connection;

    public CoffeeShopDao(Connection connection) {
        this.connection = connection;
    }

    public List<CoffeeShop> findActive() throws SQLException {
        String sql = """
                SELECT shop_id, name
                FROM coffee_shops
                WHERE is_active = ?
                ORDER BY name, shop_id
                """;

        List<CoffeeShop> shops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    shops.add(new CoffeeShop(
                        resultSet.getLong("shop_id"), resultSet.getString("name")
                    ));
                }
            }
        }
        return shops;
    }

    public boolean lockActiveById(long shopId) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new SQLException("Locking a shop requires a service transaction.");
        }

        String sql = """
                SELECT shop_id
                FROM coffee_shops
                WHERE shop_id = ? AND is_active = TRUE
                FOR SHARE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
