package com.coffeeallocation;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.service.AllocationConfirmationService;
import com.coffeeallocation.service.SharedAllocationService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in only: a NEW empty disposable PostgreSQL database, never application data. */
class CurrentDemoIntegrationTest {
    private static final List<String> TABLES = List.of("coffee_shops", "suppliers", "coffee_products",
            "supplier_products", "supplier_weekly_supply", "demands", "demand_items",
            "recommendation_runs", "supplier_scores", "allocations", "orders", "order_items", "supplier_reviews");

    @Test void rollingFixtureAndConsoleWorkWithoutFixedDatesOrIds() throws Exception {
        assumeTrue("true".equals(System.getenv("COFFEE_CURRENT_DEMO_INTEGRATION")));
        try (Connection connection = DatabaseConnection.getConnection(); var sql = connection.createStatement()) {
            try (var row = sql.executeQuery("SELECT current_database(), to_regclass('public.coffee_shops') IS NULL")) {
                assertTrue(row.next());
                assertEquals("coffee_current_demo_test", row.getString(1));
                assertTrue(row.getBoolean(2), "Use a NEW empty database for each invocation");
            }
            sql.execute(Files.readString(Path.of("database/01_schema.sql")));
            // Real installations need not start identity values at 1.
            for (String table : List.of("coffee_shops", "suppliers", "coffee_products")) {
                String id = switch (table) {
                    case "coffee_shops" -> "shop_id";
                    case "suppliers" -> "supplier_id";
                    default -> "product_id";
                };
                sql.execute("SELECT setval(pg_get_serial_sequence('" + table + "','" + id + "'), 100, true)");
            }
            String fixture = Files.readString(Path.of("database/04_current_demo_data.sql"));
            verifyIsoRolloverFixtures(connection, fixture);
            sql.execute(fixture);
            LocalDate today;
            try (var row = sql.executeQuery("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date")) {
                assertTrue(row.next()); today = row.getObject(1, LocalDate.class);
            }
            int year = today.get(IsoFields.WEEK_BASED_YEAR), week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            LocalDate next = today.plusWeeks(1);
            int nextYear = next.get(IsoFields.WEEK_BASED_YEAR), nextWeek = next.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            assertEquals(2, scalar(connection, "SELECT count(*) FROM coffee_shops WHERE shop_id > 100"));
            assertEquals(12, scalar(connection, "SELECT count(*) FROM supplier_weekly_supply"));
            assertEquals(5, scalar(connection, "SELECT count(*) FROM demands WHERE status='OPEN'"));
            assertEquals(0, scalar(connection, "SELECT count(*) FROM supplier_weekly_supply WHERE initial_quantity<>remaining_quantity"));
            String beforeRetry = fingerprint(connection);
            SQLException refusal = assertThrows(SQLException.class, () -> sql.execute(fixture));
            assertTrue(refusal.getMessage().contains("already contains data"));
            sql.execute("ROLLBACK");
            assertEquals(beforeRetry, fingerprint(connection), "Refusing a second seed must preserve all 13 tables");

            var preview = new SharedAllocationService().preview(year, week);
            assertEquals(5, preview.getItems().size());
            assertEquals(3L, preview.getItems().stream().filter(item -> !item.getAllocations().isEmpty()).count());
            var result = new AllocationConfirmationService().confirm(year, week, preview);
            assertEquals(5, result.orderIds().size());
            assertEquals(2, result.allocatedDemandIds().size());
            assertEquals(2, scalar(connection, "SELECT count(*) FROM demands WHERE status='ALLOCATED'"));
            assertEquals(0, scalar(connection, "SELECT count(*) FROM supplier_weekly_supply WHERE year=" + nextYear
                    + " AND week=" + nextWeek + " AND remaining_quantity<>initial_quantity"));

            long shop = scalar(connection, "SELECT shop_id FROM coffee_shops WHERE name='Demo Riverside Coffee'");
            long arabica = scalar(connection, "SELECT product_id FROM coffee_products WHERE name='Demo Arabica'");
            long futureItem = scalar(connection, "SELECT di.demand_item_id FROM demand_items di JOIN demands d USING(demand_id)"
                    + " WHERE d.required_year=" + nextYear + " AND d.required_week=" + nextWeek + " AND di.product_id=" + arabica);
            LocalDate past = today.minusWeeks(1);
            String console = runConsole("13\n" + nextYear + "\n" + nextWeek + "\n2\n" + arabica
                    + "\n3\n6\n" + futureItem + "\n7\n8\ny\n13\n" + year + "\n" + week
                    + "\n5\n" + shop + "\n" + arabica + "\n1.25\nn\ny\n13\n"
                    + past.get(IsoFields.WEEK_BASED_YEAR) + "\n" + past.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + "\n7\n0\n");
            assertTrue(console.contains("Selected delivery period: ISO week " + nextWeek + "/" + nextYear));
            assertTrue(console.contains("Total eligible supply: 240.00 kg"), console);
            assertTrue(console.contains("Confirmation committed."), console);
            assertTrue(console.contains("Demand saved. ID:"), console);
            assertTrue(console.contains("Request rejected:"), console);
            assertTrue(console.contains("Goodbye!"), console);
            assertFalse(console.contains("Database error:"), console);
            assertEquals(7, scalar(connection, "SELECT count(*) FROM orders"));
            assertEquals(1, scalar(connection, "SELECT count(*) FROM demands d JOIN demand_items di USING(demand_id)"
                    + " WHERE d.required_year=" + year + " AND d.required_week=" + week + " AND di.quantity=1.25"));
            assertEquals(0, scalar(connection, "SELECT count(*) FROM supplier_weekly_supply sw LEFT JOIN"
                    + " (SELECT supply_id, sum(allocated_quantity) AS used FROM allocations GROUP BY supply_id) a USING(supply_id)"
                    + " WHERE sw.remaining_quantity<>sw.initial_quantity-coalesce(a.used,0)"));
        }
    }

    private static void verifyIsoRolloverFixtures(Connection connection, String fixture) throws SQLException {
        // Simulate the seed clock only in these rolled-back test copies, never in the shipped SQL.
        for (LocalDate date : List.of(LocalDate.of(2020,12,28), LocalDate.of(2025,12,29),
                LocalDate.of(2026,12,28), LocalDate.of(2027,1,4))) {
            String datedFixture = fixture.replace("\r\n", "\n")
                    .replace("BEGIN;\n", "").replace("COMMIT;\n", "")
                    .replace("(clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE", "DATE '" + date + "'")
                    .replace("date_trunc('week', clock_timestamp() AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE",
                            "date_trunc('week', DATE '" + date + "'::TIMESTAMP)::DATE");
            try (var sql = connection.createStatement()) {
                sql.execute("BEGIN");
                try {
                    sql.execute(datedFixture);
                    LocalDate next = date.plusWeeks(1);
                    assertEquals(5, scalar(connection, "SELECT count(*) FROM demand_items di JOIN demands d USING(demand_id)"
                            + " JOIN supplier_products sp USING(product_id) JOIN supplier_weekly_supply sw ON sw.supplier_product_id=sp.supplier_product_id"
                            + " AND sw.year=d.required_year AND sw.week=d.required_week WHERE sp.is_active AND d.required_year="
                            + next.get(IsoFields.WEEK_BASED_YEAR) + " AND d.required_week=" + next.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
                    assertEquals(4, scalar(connection, "SELECT count(*) FROM demands WHERE required_year="
                            + date.get(IsoFields.WEEK_BASED_YEAR) + " AND required_week=" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
                    assertEquals(1, scalar(connection, "SELECT count(*) FROM demands WHERE required_year="
                            + next.get(IsoFields.WEEK_BASED_YEAR) + " AND required_week=" + next.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
                } finally {
                    sql.execute("ROLLBACK");
                }
            }
        }
    }

    private static long scalar(Connection connection, String query) throws SQLException {
        try (var sql = connection.createStatement(); var row = sql.executeQuery(query)) {
            assertTrue(row.next()); return row.getLong(1);
        }
    }

    private static String fingerprint(Connection connection) throws SQLException {
        var result = new StringBuilder();
        for (String table : TABLES) { // Identifiers come only from the fixed table list above.
            try (var sql = connection.createStatement(); var row = sql.executeQuery(
                    "SELECT md5(coalesce(string_agg(row_to_json(t)::text, '|' ORDER BY row_to_json(t)::text),'')) FROM " + table + " t")) {
                assertTrue(row.next()); result.append(row.getString(1));
            }
        }
        return result.toString();
    }

    private static String runConsole(String input) {
        var previousIn = System.in; var previousOut = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output));
            Main.main(new String[0]);
        } finally {
            System.setIn(previousIn); System.setOut(previousOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
