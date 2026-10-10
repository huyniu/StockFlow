package com.stockflow.auth.security;

import com.stockflow.user.domain.User;
import java.util.Locale;
import java.util.Set;

/** Public operator identities require an explicit, persisted owner recovery. */
public final class DemoAccountPolicy {
    private static final Set<String> PUBLIC_OPERATORS = Set.of(
            "admin@stockflow.com", "manager@stockflow.com", "staff.hn@stockflow.com");

    private DemoAccountPolicy() {}

    public static boolean isPublicOperator(User user) {
        return (isPublicOperatorEmail(user.getEmail()) && user.getOperatorRecoveredAt() == null)
                || ("customer@stockflow.com".equals(user.getEmail().trim().toLowerCase(Locale.ROOT))
                    && !"CUSTOMER".equals(user.getRole().getName()));
    }

    public static boolean isPublicOperatorEmail(String email) {
        return PUBLIC_OPERATORS.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isReservedEmail(String email) {
        return isPublicOperatorEmail(email) || "customer@stockflow.com".equals(email.trim().toLowerCase(Locale.ROOT));
    }

    // Fingerprints of published demo passwords, never usable as recovery credentials.
    private static final Set<String> LEGACY_PASSWORD_FINGERPRINTS = Set.of(
            "e86f78a8a3caf0b60d8e74e5942aa6d86dc150cd3c03338aef25b7d2d7e3acc7",
            "e8392925a98c9c22795d1fc5d0dfee5b9a6943f6b768ec5a2a0c077e5ed119cf",
            "dfd48f36338aa36228ebb9e204bba6b4e18db0b623e25c458901edc831fb18e9",
            "98ec654a8df28f8f0f8f02220483d46916b85017de0b74d8ec755c28cb8539a8");

    public static boolean isLegacyPassword(String password) {
        if (password == null) return false;
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return LEGACY_PASSWORD_FINGERPRINTS.contains(java.util.HexFormat.of().formatHex(
                    digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
