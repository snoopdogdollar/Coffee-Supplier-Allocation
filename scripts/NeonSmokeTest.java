import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.CoffeeShopDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.WeeklySupplyDao;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/** Checks the actual application's JDBC connection and DAOs without persistent writes. */
public class NeonSmokeTest {
    public static void main(String[] args) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            check(connection.isValid(10), "JDBC connection is valid");
            // Neon terminates client TLS at its proxy. pg_stat_ssl describes
            // the backend hop, so check the actual JDBC transport requirements.
            // pgJDBC refuses this connection if TLS/channel binding is unavailable.
            String jdbcUrl = connection.getMetaData().getURL();
            check(jdbcUrl.matches(".*[?&]sslmode=(require|verify-ca|verify-full)(&.*)?$"),
                "JDBC requires TLS");
            check(jdbcUrl.matches(".*[?&]channelBinding=require(&.*)?$"),
                "JDBC requires channel binding");

            Set<String> expected = Set.of("coffee_shops", "suppliers", "coffee_products",
                "supplier_products", "supplier_weekly_supply", "demands", "demand_items",
                "recommendation_runs", "supplier_scores", "allocations", "orders",
                "order_items", "supplier_reviews");
            Set<String> found = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
                while (result.next()) found.add(result.getString(1));
            }
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(found);
            check(missing.isEmpty(), "All 13 application tables exist; missing=" + missing);

            var products = new CoffeeProductDao(connection).findActive();
            var shops = new CoffeeShopDao(connection).findActive();
            var demands = new DemandDao(connection).findOpenItemsForWeek(2026, 42);
            long productId = products.isEmpty() ? Long.MAX_VALUE : products.get(0).getProductId();
            var supplies = new WeeklySupplyDao(connection).findEligible(productId, 2026, 42);
            System.out.printf("PASS: app DAO reads (products=%d, shops=%d, demand items=%d, supplies=%d)%n",
                products.size(), shops.size(), demands.size(), supplies.size());

            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE neon_setup_probe (value integer) ON COMMIT DROP");
                statement.executeUpdate("INSERT INTO neon_setup_probe VALUES (42)");
                try (ResultSet result = statement.executeQuery("SELECT value FROM neon_setup_probe")) {
                    check(result.next() && result.getInt(1) == 42, "Postgres write/read transaction");
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                     "SELECT to_regclass('pg_temp.neon_setup_probe') IS NULL")) {
                check(result.next() && result.getBoolean(1), "Probe transaction rolled back");
            }
            System.out.println("PASS: Neon Postgres end-to-end smoke test");
        } catch (SQLException exception) {
            System.err.println("FAIL: Neon JDBC smoke test; SQLState=" + exception.getSQLState());
            System.exit(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("FAIL: " + message);
        System.out.println("PASS: " + message);
    }
}
