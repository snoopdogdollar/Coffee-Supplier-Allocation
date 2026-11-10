package com.coffeeallocation.dao;

import com.coffeeallocation.model.OpenDemandItem;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DemandDao {

	private final Connection connection;

	public DemandDao(Connection connection) {
		this.connection = connection;
	}

	public List<OpenDemandItem> findOpenItemsForWeek(
	    int year, int week) throws SQLException {

		String sql = """
		SELECT di.demand_item_id,
		d.demand_id,
		cs.shop_id,
		cs.name AS shop_name,
		p.product_id,
		p.name AS product_name,
		di.quantity,
		p.unit
		FROM demands d
		JOIN demand_items di
		ON di.demand_id = d.demand_id
		JOIN coffee_shops cs
		ON cs.shop_id = d.shop_id
		JOIN coffee_products p
		ON p.product_id = di.product_id
		WHERE d.required_year = ?
		AND d.required_week = ?
		AND d.status = ?
		AND cs.is_active = TRUE
		AND p.is_active = TRUE
		ORDER BY cs.name, d.demand_id, di.demand_item_id
		""";

		List<OpenDemandItem> items = new ArrayList<>();

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, year);
			statement.setInt(2, week);
			statement.setString(3, "OPEN");

			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					items.add(new OpenDemandItem(
					              resultSet.getLong("demand_item_id"),
					              resultSet.getLong("demand_id"),
					              resultSet.getLong("shop_id"),
					              resultSet.getString("shop_name"),
					              resultSet.getLong("product_id"),
					              resultSet.getString("product_name"),
					              resultSet.getBigDecimal("quantity"),
					              resultSet.getString("unit")
					          ));
				}
			}
		}

		return items;
	}

	public long insertDemand(
	    long shopId,
	    LocalDate requestDate,
	    int year,
	    int week,
	    String status,
	    String note) throws SQLException {

		if (connection.getAutoCommit()) {
			throw new SQLException(
			    "Service must disable auto-commit before inserting a demand."
			);
		}

		String sql = """
		INSERT INTO demands (
		    shop_id,
		    request_date,
		    required_year,
		    required_week,
		    status,
		    note
		)
		VALUES (?, ?, ?, ?, ?, ?)
		RETURNING demand_id
		""";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, shopId);
			statement.setObject(2, requestDate);
			statement.setInt(3, year);
			statement.setInt(4, week);
			statement.setString(5, status);
			statement.setString(6, note);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Insert did not return a demand ID.");
				}

				return resultSet.getLong("demand_id");
			}
		}
	}

    public List<OpenDemandItem> findOpenUnallocatedItemsForWeek(
            int year, int week) throws SQLException {
        String sql = """
                SELECT di.demand_item_id, d.demand_id,
                       cs.shop_id, cs.name AS shop_name,
                       p.product_id, p.name AS product_name,
                       di.quantity, p.unit
                FROM demands d
                JOIN demand_items di ON di.demand_id = d.demand_id
                JOIN coffee_shops cs ON cs.shop_id = d.shop_id
                JOIN coffee_products p ON p.product_id = di.product_id
                WHERE d.required_year = ?
                  AND d.required_week = ?
                  AND d.status = ?
                  AND cs.is_active = TRUE
                  AND p.is_active = TRUE
                  AND NOT EXISTS (
                      SELECT 1 FROM allocations a
                      WHERE a.demand_item_id = di.demand_item_id
                  )
                ORDER BY d.request_date, d.demand_id, di.demand_item_id
                """;
        List<OpenDemandItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            statement.setInt(2, week);
            statement.setString(3, "OPEN");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(new OpenDemandItem(
                            resultSet.getLong("demand_item_id"),
                            resultSet.getLong("demand_id"),
                            resultSet.getLong("shop_id"),
                            resultSet.getString("shop_name"),
                            resultSet.getLong("product_id"),
                            resultSet.getString("product_name"),
                            resultSet.getBigDecimal("quantity"),
                            resultSet.getString("unit")));
                }
            }
        }
        return items;
    }

	public void lockDemandsForWeek(int year, int week)
        throws SQLException {

    if (connection.getAutoCommit()) {
        throw new SQLException(
                "Service must start a transaction before locking demands."
        );
    }

    String demandSql = """
            SELECT demand_id
            FROM demands
            WHERE required_year = ?
              AND required_week = ?
            ORDER BY demand_id
            FOR UPDATE
            """;

    try (PreparedStatement statement =
            connection.prepareStatement(demandSql)) {

        statement.setInt(1, year);
        statement.setInt(2, week);

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // Read every row to acquire all demand locks.
            }
        }
    }

    String itemSql = """
            SELECT di.demand_item_id
            FROM demand_items di
            JOIN demands d ON d.demand_id = di.demand_id
            WHERE d.required_year = ?
              AND d.required_week = ?
            ORDER BY di.demand_item_id
            FOR UPDATE OF di
            """;

    try (PreparedStatement statement =
            connection.prepareStatement(itemSql)) {

        statement.setInt(1, year);
        statement.setInt(2, week);

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // Read every row to acquire all demand item locks.
            }
        }
    }
}

    public record RecentDemand(long id, LocalDate requestDate, int requiredYear,
                               int requiredWeek, String status, String note,
                               List<RecentItem> items) { }

    public record RecentItem(long productId, String productName, String unit,
                             BigDecimal quantity, String note) { }

    public List<RecentDemand> findRecentByShop(long shopId) throws SQLException {
        String sql = """
                WITH recent AS (
                    SELECT demand_id, request_date, required_year, required_week,
                           status, note, created_at
                    FROM demands
                    WHERE shop_id = ?
                    ORDER BY created_at DESC, demand_id DESC
                    LIMIT 20
                )
                SELECT recent.demand_id, recent.request_date, recent.required_year,
                       recent.required_week, recent.status, recent.note AS demand_note,
                       di.product_id, p.name AS product_name, p.unit,
                       di.quantity, di.note AS item_note
                FROM recent
                LEFT JOIN demand_items di ON di.demand_id = recent.demand_id
                LEFT JOIN coffee_products p ON p.product_id = di.product_id
                ORDER BY recent.created_at DESC, recent.demand_id DESC, di.demand_item_id
                """;

        List<RecentDemand> demands = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                RecentDemand current = null;
                while (resultSet.next()) {
                    long demandId = resultSet.getLong("demand_id");
                    if (current == null || current.id() != demandId) {
                        current = new RecentDemand(demandId,
                                resultSet.getObject("request_date", LocalDate.class),
                                resultSet.getInt("required_year"),
                                resultSet.getInt("required_week"),
                                resultSet.getString("status"),
                                resultSet.getString("demand_note"),
                                new ArrayList<>());
                        demands.add(current);
                    }

                    Long productId = resultSet.getObject("product_id", Long.class);
                    if (productId != null) {
                        current.items().add(new RecentItem(productId,
                                resultSet.getString("product_name"),
                                resultSet.getString("unit"),
                                resultSet.getBigDecimal("quantity"),
                                resultSet.getString("item_note")));
                    }
                }
            }
        }
        return demands;
    }
}
