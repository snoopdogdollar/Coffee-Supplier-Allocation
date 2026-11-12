package com.coffeemongers.view;

import java.sql.SQLException;
import java.util.Set;

/** User-facing database diagnostics that exclude driver messages and credentials. */
public final class DatabaseMessages {
    private static final Set<String> MISSING_SETTINGS = Set.of(
            "Missing environment variable: DB_URL",
            "Missing environment variable: DB_USER",
            "Missing environment variable: DB_PASSWORD");

    private DatabaseMessages() {}

    public static String describe(Throwable error, String fallback) {
        if (error instanceof IllegalArgumentException) {
            return error.getMessage() == null ? fallback : error.getMessage();
        }
        if (error instanceof IllegalStateException && MISSING_SETTINGS.contains(
                error.getMessage() == null ? "" : error.getMessage())) {
            return error.getMessage() + ". Set it in the PowerShell terminal that starts the app, then restart.";
        }
        if (!(error instanceof SQLException sql)) return fallback;
        String state = sql.getSQLState();
        if (state == null || !state.matches("[0-9A-Z]{5}")) return fallback;
        String message = switch (state) {
            case "28P01" -> "PostgreSQL authentication failed. Check DB_USER and re-enter its PostgreSQL password in DB_PASSWORD.";
            case "28000" -> "PostgreSQL rejected authentication. Check DB_USER, DB_PASSWORD and the server access rules.";
            case "3D000" -> "The database named in DB_URL does not exist. Use the database name shown in pgAdmin.";
            case "42P01", "3F000" -> "Connected to PostgreSQL, but the required tables or schema were not found. Check the database in DB_URL and its schema.";
            case "42501" -> "Connected to PostgreSQL, but DB_USER lacks permission to read or update the required tables.";
            case "08004" -> "PostgreSQL rejected the connection setup. Check DB_USER, DB_PASSWORD and the connection options.";
            case "57P03" -> "PostgreSQL is not ready to accept connections. Check that the server is running.";
            default -> state.startsWith("08")
                    ? "Could not connect to PostgreSQL. Check the server, host and port in DB_URL."
                    : fallback;
        };
        return message + " [SQLSTATE " + state + "]";
    }
}
