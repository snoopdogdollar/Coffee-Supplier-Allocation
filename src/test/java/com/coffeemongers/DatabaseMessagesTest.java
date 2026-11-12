package com.coffeemongers;

import com.coffeemongers.view.DatabaseMessages;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseMessagesTest {
    @Test void identifiesMissingLaunchSettings() {
        assertTrue(DatabaseMessages.describe(new IllegalStateException(
                "Missing environment variable: DB_PASSWORD"), "fallback")
                .contains("Missing environment variable: DB_PASSWORD"));
    }

    @Test void identifiesAuthenticationAndDatabaseErrorsWithoutExposingDriverText() {
        String secret = "jdbc:postgresql://example/private?password=private-value";
        String auth = DatabaseMessages.describe(new SQLException(secret, "28P01"), "fallback");
        assertTrue(auth.contains("authentication failed"));
        assertTrue(auth.contains("28P01"));
        assertFalse(auth.contains(secret));
        assertFalse(auth.contains("private-value"));
        assertTrue(DatabaseMessages.describe(new SQLException(secret, "3D000"), "fallback")
                .contains("does not exist"));
        assertTrue(DatabaseMessages.describe(new SQLException(secret, "08001"), "fallback")
                .contains("host and port"));
    }

    @Test void distinguishesSchemaAndPermissionErrorsFromConnectivity() {
        assertTrue(DatabaseMessages.describe(new SQLException("sensitive", "42P01"), "fallback")
                .contains("tables or schema"));
        assertTrue(DatabaseMessages.describe(new SQLException("sensitive", "42501"), "fallback")
                .contains("lacks permission"));
        assertEquals("validation message", DatabaseMessages.describe(
                new IllegalArgumentException("validation message"), "fallback"));
    }
}
