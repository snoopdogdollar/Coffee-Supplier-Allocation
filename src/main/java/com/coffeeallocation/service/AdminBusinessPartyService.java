package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.BusinessPartyCatalogDao;
import com.coffeeallocation.model.BusinessPartyDetails;
import com.coffeeallocation.model.BusinessPartyType;
import java.sql.SQLException;
import java.util.List;

public final class AdminBusinessPartyService {
    private final BusinessPartyType type;

    public AdminBusinessPartyService(BusinessPartyType type) {
        if (type == null) throw new IllegalArgumentException("Choose Suppliers or Coffee Shops.");
        this.type = type;
    }

    public List<BusinessPartyDetails> load() throws SQLException {
        return transaction(true, BusinessPartyCatalogDao::findAll);
    }

    public long add(String name, String contactPerson, String phone, String email, String address) throws SQLException {
        String validName = field(name, "Name", 100, true);
        String validContact = field(contactPerson, "Contact person", 100, false);
        String validPhone = field(phone, "Phone", 20, false);
        String validEmail = field(email, "Email", 100, false);
        String validAddress = field(address, "Address", 255, false);
        return transaction(false, dao -> dao.insert(validName, validContact, validPhone, validEmail, validAddress));
    }

    public void changeStatus(long id, boolean expectedActive) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Select a valid catalog record.");
        transaction(false, dao -> { dao.changeStatus(id, expectedActive); return null; });
    }

    private static String field(String input, String name, int limit, boolean required) {
        String value = input == null ? "" : input.strip();
        if (required && value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " contains an unsupported character.");
        if (value.codePointCount(0, value.length()) > limit) {
            throw new IllegalArgumentException(name + " must contain at most " + limit + " characters.");
        }
        return value.isEmpty() && !required ? null : value;
    }

    @FunctionalInterface
    private interface Operation<T> { T run(BusinessPartyCatalogDao dao) throws SQLException; }

    private <T> T transaction(boolean readOnly, Operation<T> operation) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(readOnly);
            connection.setAutoCommit(false);
            try {
                T result = operation.run(new BusinessPartyCatalogDao(connection, type));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
}
