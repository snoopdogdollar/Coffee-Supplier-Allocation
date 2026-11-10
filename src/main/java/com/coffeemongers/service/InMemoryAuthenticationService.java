package com.coffeemongers.service;

import com.coffeemongers.model.auth.AccountRole;
import com.coffeemongers.model.auth.DemoUser;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Session-only demo identity store. Passwords are salted PBKDF2 hashes, never retained as plaintext. */
public final class InMemoryAuthenticationService implements AuthenticationService {
    public static final String ADMIN_EMAIL = "admin@coffeemongers.demo";
    public static final String ADMIN_PASSWORD = "CoffeeAdmin2026!";
    static final String TEST_BUYER_ID = "integration-buyer";
    static final String TEST_BUYER_EMAIL = "buyer@coffeemongers.test";
    static final String TEST_ADMIN_ID = "integration-admin";
    static final String TEST_ADMIN_EMAIL = "admin@coffeemongers.test";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private final Map<String, Credential> credentials = new HashMap<>();
    private final SessionService sessions;
    private final SecureRandom random = new SecureRandom();

    public InMemoryAuthenticationService(SessionService sessions) {
        this(sessions, null, null);
    }

    InMemoryAuthenticationService(SessionService sessions, String testBuyerPassword, String testAdminPassword) {
        this.sessions = Objects.requireNonNull(sessions);
        DemoUser admin = new DemoUser("demo-admin", "Coffee Mongers Admin", ADMIN_EMAIL, AccountRole.ADMIN);
        credentials.put(ADMIN_EMAIL, credential(admin, ADMIN_PASSWORD.toCharArray()));
        addTestAccount(new DemoUser(TEST_BUYER_ID, "Test Buyer", TEST_BUYER_EMAIL, AccountRole.BUYER),
                testBuyerPassword);
        addTestAccount(new DemoUser(TEST_ADMIN_ID, "Test Admin", TEST_ADMIN_EMAIL, AccountRole.ADMIN),
                testAdminPassword);
    }

    private void addTestAccount(DemoUser user, String configuredPassword) {
        if (configuredPassword == null) {
            return;
        }
        char[] password = configuredPassword.toCharArray();
        try {
            validatePassword(password);
            credentials.put(user.email(), credential(user, password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override public synchronized DemoUser login(String email, char[] password) {
        String normalized = normalizeEmail(email);
        Credential credential = credentials.get(normalized);
        if (credential == null || password == null || !verify(password, credential))
            throw new IllegalArgumentException("Email or password is incorrect.");
        sessions.start(credential.user());
        return credential.user();
    }

    @Override public synchronized DemoUser register(String fullName, String email, char[] password, AccountRole role) {
        String name = fullName == null ? "" : fullName.trim();
        String normalized = normalizeEmail(email);
        if (name.length() < 2) throw new IllegalArgumentException("Enter your full name.");
        if (name.length() > 100) throw new IllegalArgumentException("Full name must be 100 characters or fewer.");
        if (!EMAIL.matcher(normalized).matches()) throw new IllegalArgumentException("Enter a valid email address.");
        if (credentials.containsKey(normalized)) throw new IllegalArgumentException("An account with this email already exists.");
        if (role == null || role == AccountRole.ADMIN) throw new IllegalArgumentException("Choose Buyer or Supplier for a public demo account.");
        validatePassword(password);
        DemoUser user = new DemoUser(UUID.randomUUID().toString(), name, normalized, role);
        credentials.put(normalized, credential(user, password));
        sessions.start(user);
        return user;
    }

    private void validatePassword(char[] password) {
        if (password == null || password.length < 10) throw new IllegalArgumentException("Password must be at least 10 characters.");
        boolean upper=false, lower=false, digit=false, special=false;
        for(char c:password){upper|=Character.isUpperCase(c);lower|=Character.isLowerCase(c);digit|=Character.isDigit(c);special|=!Character.isLetterOrDigit(c);}
        if (!(upper&&lower&&digit&&special)) throw new IllegalArgumentException("Password needs an uppercase letter, lowercase letter, number, and symbol.");
    }

    private Credential credential(DemoUser user, char[] password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        byte[] hash = derive(password, salt);
        return new Credential(user, salt, hash);
    }
    private boolean verify(char[] password, Credential saved) { return MessageDigest.isEqual(derive(password, saved.salt()), saved.hash()); }
    private byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Secure demo password hashing is unavailable.", e); }
        finally { spec.clearPassword(); }
    }
    private static String normalizeEmail(String email) { return Normalizer.normalize(email == null ? "" : email.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    private record Credential(DemoUser user, byte[] salt, byte[] hash) { }
}
