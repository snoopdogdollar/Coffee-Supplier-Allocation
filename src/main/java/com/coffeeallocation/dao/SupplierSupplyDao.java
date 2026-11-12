package com.coffeeallocation.dao;

import com.coffeeallocation.model.SupplierSupplyView;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SupplierSupplyDao {
    private final Connection connection;

    public SupplierSupplyDao(Connection connection) {
        this.connection = connection;
    }

    public SupplierSupplyView.Catalog catalog(long supplierId) throws SQLException {
        String sql = """
                SELECT s.name AS supplier_name, sp.supplier_product_id, p.product_id, p.name, p.unit
                FROM suppliers s
                LEFT JOIN (supplier_products sp JOIN coffee_products p
                           ON p.product_id = sp.product_id AND p.is_active)
                    ON sp.supplier_id = s.supplier_id AND sp.is_active
                WHERE s.supplier_id = ? AND s.is_active
                ORDER BY p.name, p.product_id
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Supplier does not exist or is inactive.");
                }
                String name = rows.getString("supplier_name");
                List<SupplierSupplyView.Product> products = new ArrayList<>();
                do {
                    if (rows.getObject("supplier_product_id") != null) {
                        products.add(new SupplierSupplyView.Product(rows.getLong("supplier_product_id"),
                                rows.getLong("product_id"), rows.getString("name"), rows.getString("unit")));
                    }
                } while (rows.next());
                return new SupplierSupplyView.Catalog(name, products);
            }
        }
    }

    public List<SupplierSupplyView.Row> findWeek(long supplierId, int year, int week) throws SQLException {
        String sql = """
                SELECT sw.supply_id, sw.supplier_product_id, p.name, p.unit, sw.year, sw.week,
                       sw.initial_quantity, sw.remaining_quantity, sw.unit_price
                FROM supplier_weekly_supply sw
                JOIN supplier_products sp ON sp.supplier_product_id = sw.supplier_product_id
                JOIN coffee_products p ON p.product_id = sp.product_id
                WHERE sp.supplier_id = ? AND sw.year = ? AND sw.week = ?
                ORDER BY p.name, p.product_id, sw.supply_id
                """;
        List<SupplierSupplyView.Row> result = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            statement.setInt(2, year);
            statement.setInt(3, week);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapRow(rows));
                }
            }
        }
        return List.copyOf(result);
    }

    public void lockActiveProduct(long supplierId, long supplierProductId) throws SQLException {
        requireTransaction();
        String sql = """
                SELECT sp.supplier_product_id
                FROM supplier_products sp
                JOIN suppliers s ON s.supplier_id = sp.supplier_id
                JOIN coffee_products p ON p.product_id = sp.product_id
                WHERE sp.supplier_id = ? AND sp.supplier_product_id = ?
                  AND s.is_active AND sp.is_active AND p.is_active
                FOR SHARE OF s, sp, p
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            statement.setLong(2, supplierProductId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Choose an active product linked to this supplier.");
                }
            }
        }
    }

    public void lockActiveSupplier(long supplierId) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement(
                "SELECT supplier_id FROM suppliers WHERE supplier_id = ? AND is_active FOR SHARE")) {
            statement.setLong(1, supplierId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Supplier does not exist or is inactive.");
                }
            }
        }
    }

    public SupplierSupplyView.Row lockSupply(long supplierId, long supplyId) throws SQLException {
        requireTransaction();
        String sql = """
                SELECT sw.supply_id, sw.supplier_product_id, p.name, p.unit, sw.year, sw.week,
                       sw.initial_quantity, sw.remaining_quantity, sw.unit_price
                FROM supplier_weekly_supply sw
                JOIN supplier_products sp ON sp.supplier_product_id = sw.supplier_product_id
                JOIN coffee_products p ON p.product_id = sp.product_id
                WHERE sp.supplier_id = ? AND sw.supply_id = ?
                FOR UPDATE OF sw
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierId);
            statement.setLong(2, supplyId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("This supplier has no supply record with that ID.");
                }
                return mapRow(rows);
            }
        }
    }

    public long insert(long supplierProductId, int year, int week, BigDecimal quantity, BigDecimal price)
            throws SQLException {
        requireTransaction();
        String sql = """
                INSERT INTO supplier_weekly_supply
                    (supplier_product_id, year, week, initial_quantity, remaining_quantity, unit_price)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (supplier_product_id, year, week) DO NOTHING
                RETURNING supply_id
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, supplierProductId);
            statement.setInt(2, year);
            statement.setInt(3, week);
            statement.setBigDecimal(4, quantity);
            statement.setBigDecimal(5, quantity);
            statement.setBigDecimal(6, price);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("Supply already exists for this product and week. Refresh and select it to edit.");
                }
                return rows.getLong("supply_id");
            }
        }
    }

    public void update(long supplyId, BigDecimal quantity, BigDecimal remaining, BigDecimal price)
            throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("""
                UPDATE supplier_weekly_supply
                SET initial_quantity = ?, remaining_quantity = ?, unit_price = ?
                WHERE supply_id = ?
                """)) {
            statement.setBigDecimal(1, quantity);
            statement.setBigDecimal(2, remaining);
            statement.setBigDecimal(3, price);
            statement.setLong(4, supplyId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The locked supply record could not be updated.");
            }
        }
    }

    public boolean hasAllocations(long supplyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM allocations WHERE supply_id = ?)")) {
            statement.setLong(1, supplyId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    public void delete(long supplyId) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("DELETE FROM supplier_weekly_supply WHERE supply_id = ?")) {
            statement.setLong(1, supplyId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("The locked supply record could not be deleted.");
            }
        }
    }

    private static SupplierSupplyView.Row mapRow(java.sql.ResultSet row) throws SQLException {
        return new SupplierSupplyView.Row(row.getLong("supply_id"), row.getLong("supplier_product_id"),
                row.getString("name"), row.getString("unit"), row.getInt("year"), row.getInt("week"),
                row.getBigDecimal("initial_quantity"), row.getBigDecimal("remaining_quantity"),
                row.getBigDecimal("unit_price"));
    }

    private void requireTransaction() throws SQLException {
        if (connection.getAutoCommit()) {
            throw new SQLException("Supplier supply writes and locks require a service transaction.");
        }
    }

    public boolean offerProduct(long supplierId, long productId) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("""
                INSERT INTO supplier_products AS offering (supplier_id, product_id, is_active)
                VALUES (?, ?, TRUE)
                ON CONFLICT (supplier_id, product_id)
                DO UPDATE SET is_active = TRUE WHERE NOT offering.is_active
                """)) {
            statement.setLong(1, supplierId);
            statement.setLong(2, productId);
            return statement.executeUpdate() == 1;
        }
    }

    public void stopOffering(long supplierId, long supplierProductId) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("""
                UPDATE supplier_products SET is_active = FALSE
                WHERE supplier_id = ? AND supplier_product_id = ?
                """)) {
            statement.setLong(1, supplierId);
            statement.setLong(2, supplierProductId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Choose a product linked to this supplier.");
            }
        }
    }
}
