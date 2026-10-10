package com.stockflow.auth.security;

import com.stockflow.user.domain.User;
import java.util.Locale;
import java.util.Set;

/** Những định danh có mật khẩu/quyền quản trị từng được phát công khai không được tái sử dụng. */
public final class DemoAccountPolicy {
    private static final Set<String> PUBLIC_OPERATORS = Set.of(
            "admin@stockflow.com", "manager@stockflow.com", "staff.hn@stockflow.com");

    private DemoAccountPolicy() {}

    public static boolean isPublicOperator(User user) {
        return isPublicOperatorEmail(user.getEmail())
                || ("customer@stockflow.com".equals(user.getEmail().trim().toLowerCase(Locale.ROOT))
                    && !"CUSTOMER".equals(user.getRole().getName()));
    }

    public static boolean isPublicOperatorEmail(String email) {
        return PUBLIC_OPERATORS.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isReservedEmail(String email) {
        return isPublicOperatorEmail(email) || "customer@stockflow.com".equals(email.trim().toLowerCase(Locale.ROOT));
    }
}
