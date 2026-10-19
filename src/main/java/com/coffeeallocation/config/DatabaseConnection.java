package com.coffeeallocation.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            requireEnv("DB_URL"),
            requireEnv("DB_USER"),
            requireEnv("DB_PASSWORD")
        );
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null) {
            throw new IllegalStateException(
                "Missing environment variable: " + name
            );
        }

        return value;
    }
}