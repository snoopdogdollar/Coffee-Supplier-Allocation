package com.coffeemongers.service;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Composition root for demo UI services and configured PostgreSQL test workspaces. */
public final class AppServices {
    private static final SessionService SESSION = new InMemorySessionService();
    private static final Optional<BuyerDemandService> TEST_BUYER_DEMANDS = configureTestBuyer();
    private static final String TEST_ADMIN_PASSWORD_ENV = "COFFEE_TEST_ADMIN_PASSWORD";
    private static final AuthenticationService AUTHENTICATION = new InMemoryAuthenticationService(
            SESSION, TEST_BUYER_DEMANDS.isPresent() ? System.getenv("COFFEE_TEST_BUYER_PASSWORD") : null,
            System.getenv(TEST_ADMIN_PASSWORD_ENV));
    private static final ConcurrentHashMap<String, MarketplaceService> MARKETPLACES = new ConcurrentHashMap<>();
    private static final DemoOrderHub DEMO_ORDER_HUB = new DemoOrderHub();
    private static final String GUEST = "__guest_preview__";
    private static volatile Function<Optional<DemoUser>,MarketplaceService> marketplaceFactory=user->new InMemoryMarketplaceService(DEMO_ORDER_HUB);
    private AppServices() { }
    public static MarketplaceService marketplace() {
        String key=SESSION.currentUser().map(DemoUser::email).orElse(GUEST);
        return RoleAwareMarketplaceService.protect(MARKETPLACES.computeIfAbsent(key, ignored->marketplaceFactory.apply(SESSION.currentUser())));
    }
    public static AuthenticationService authentication() { return AUTHENTICATION; }
    public static SessionService session() { return SESSION; }
    public static Optional<DemoUser> currentUser() { return SESSION.currentUser(); }
    public static Optional<BuyerDemandService> liveBuyerDemands() {
        return SESSION.currentUser()
                .filter(user -> user.role() == AccountRole.BUYER
                        && user.userId().equals(InMemoryAuthenticationService.TEST_BUYER_ID))
                .flatMap(user -> TEST_BUYER_DEMANDS);
    }

    public static boolean buyerIntegrationEnabled() {
        return liveBuyerDemands().isPresent();
    }

    public static boolean allocationPreviewEnabled() {
        return System.getenv(TEST_ADMIN_PASSWORD_ENV) != null
                && SESSION.activeWorkspace() == AccountRole.ADMIN
                && SESSION.currentUser()
                        .filter(user -> user.role() == AccountRole.ADMIN
                                && user.userId().equals(InMemoryAuthenticationService.TEST_ADMIN_ID))
                        .isPresent();
    }

    private static Optional<BuyerDemandService> configureTestBuyer() {
        String shopId = System.getenv("COFFEE_TEST_SHOP_ID");
        if (shopId == null || shopId.isBlank()) {
            return Optional.empty();
        }
        if (System.getenv("COFFEE_TEST_BUYER_PASSWORD") == null) {
            throw new IllegalStateException("Set COFFEE_TEST_BUYER_PASSWORD to enable the PostgreSQL test buyer.");
        }
        try {
            return Optional.of(new BuyerDemandService(Long.parseLong(shopId.strip())));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("COFFEE_TEST_SHOP_ID must be a positive shop ID.", exception);
        }
    }

    public static boolean isReadOnlyWorkspacePreview(){return SESSION.currentUser().map(user->user.role()==com.coffeemongers.model.auth.AccountRole.ADMIN&&SESSION.activeWorkspace()!=com.coffeemongers.model.auth.AccountRole.ADMIN).orElse(false);}
    /** Configure an API implementation at application startup without changing FXML controllers. */
    public static synchronized void configureMarketplaceFactory(Function<Optional<DemoUser>,MarketplaceService> factory){
        marketplaceFactory=java.util.Objects.requireNonNull(factory);
        MARKETPLACES.clear();
    }
    public static String environmentStatus() {
        if (buyerIntegrationEnabled()) {
            return "POSTGRESQL TEST";
        }
        String key=SESSION.currentUser().map(DemoUser::email).orElse(GUEST);
        MarketplaceService service=MARKETPLACES.computeIfAbsent(key, ignored->marketplaceFactory.apply(SESSION.currentUser()));
        if(service instanceof InMemoryMarketplaceService||service.isDemoMode())return "DEMO MODE";
        if(service instanceof ConnectionAwareMarketplace connection)return connection.isConnected()?"LIVE":"CONNECTION ERROR";
        return "CONNECTION ERROR";
    }
}
