package com.coffeeallocation.dao;

import com.coffeeallocation.model.CoffeeShop;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CoffeeShopDao extends AbstractCatalogDao<CoffeeShop> {

    public CoffeeShopDao(Connection connection) {
        super(connection);
    }

    @Override
    protected String activeQuery() {
        return """
                SELECT shop_id, name
                FROM coffee_shops
                WHERE is_active = ?
                ORDER BY name, shop_id
                """;
    }

    @Override
    protected CoffeeShop mapRow(ResultSet row) throws SQLException {
        return new CoffeeShop(row.getLong("shop_id"), row.getString("name"));
    }

    public boolean lockActiveById(long shopId) throws SQLException {
        if (connection().getAutoCommit()) {
            throw new SQLException("Locking a shop requires a service transaction.");
        }

        String sql = """
                SELECT shop_id
                FROM coffee_shops
                WHERE shop_id = ? AND is_active = TRUE
                FOR SHARE
                """;

        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
