package com.coffeeallocation.dao;

import com.coffeeallocation.model.OpenDemandItem;
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
}