package com.coffeemongers.service;

import com.coffeemongers.model.auth.AccountRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {
    @Test void demoAdminCanSignInAndSignOut() {
        SessionService session = new InMemorySessionService();
        AuthenticationService auth = new InMemoryAuthenticationService(session);
        var user = auth.login(InMemoryAuthenticationService.ADMIN_EMAIL,
                InMemoryAuthenticationService.ADMIN_PASSWORD.toCharArray());
        assertEquals(AccountRole.ADMIN, user.role());
        assertEquals(user, session.currentUser().orElseThrow());
        session.end();
        assertTrue(session.currentUser().isEmpty());
    }

    @Test void buyerAndSupplierCanRegisterAndDuplicateEmailsAreRejected() {
        SessionService session = new InMemorySessionService();
        AuthenticationService auth = new InMemoryAuthenticationService(session);
        var buyer = auth.register("Demo Buyer", "Buyer@Example.com", "CoffeeBuyer2026!".toCharArray(), AccountRole.BUYER);
        assertEquals("buyer@example.com", buyer.email());
        assertEquals(AccountRole.BUYER, buyer.role());
        assertThrows(IllegalArgumentException.class, () -> auth.register("Another Buyer", "buyer@example.com",
                "CoffeeBuyer2026!".toCharArray(), AccountRole.SUPPLIER));
        var supplier = auth.register("Demo Supplier", "supplier@example.com", "CoffeeSupplier2026!".toCharArray(), AccountRole.SUPPLIER);
        assertEquals(AccountRole.SUPPLIER, supplier.role());
        assertThrows(IllegalArgumentException.class, () -> auth.register("Demo Admin", "other@example.com",
                "CoffeeSupplier2026!".toCharArray(), AccountRole.ADMIN));
    }

    @Test void passwordRulesAndLoginAreEnforced() {
        SessionService session = new InMemorySessionService();
        AuthenticationService auth = new InMemoryAuthenticationService(session);
        assertThrows(IllegalArgumentException.class, () -> auth.register("Buyer", "buyer@example.com", "password".toCharArray(), AccountRole.BUYER));
        assertThrows(IllegalArgumentException.class, () -> auth.login(InMemoryAuthenticationService.ADMIN_EMAIL, "wrong".toCharArray()));
        assertTrue(session.currentUser().isEmpty());
    }

    @Test void roleBoundariesAndAdminReadOnlyWorkspacePreviewAreEnforced() {
        try {
            var buyer=AppServices.authentication().register("Role Test Buyer","role-test-buyer@example.com","CoffeeBuyer2026!".toCharArray(),AccountRole.BUYER);
            assertEquals(AccountRole.BUYER,buyer.role());
            assertThrows(SecurityException.class,()->AppServices.marketplace().adminSuppliers());
            assertThrows(SecurityException.class,()->AppServices.marketplace().supplierProducts());
            assertThrows(SecurityException.class,()->AppServices.session().switchWorkspace(AccountRole.SUPPLIER));
            AppServices.session().end();
            AppServices.authentication().login(InMemoryAuthenticationService.ADMIN_EMAIL,InMemoryAuthenticationService.ADMIN_PASSWORD.toCharArray());
            AppServices.session().switchWorkspace(AccountRole.BUYER);
            assertDoesNotThrow(()->AppServices.marketplace().recentDemands());
            assertThrows(SecurityException.class,()->AppServices.marketplace().submitDemand(
                    new com.coffeemongers.model.DemandRequest("","Preview",java.time.LocalDate.now(),"","",java.util.List.of())));
        } finally { AppServices.session().end(); }
    }
}
