package com.stockflow.user.support;

import com.stockflow.user.domain.Role;
import com.stockflow.user.domain.User;

/** Tài khoản đã xác thực dành cho fixture nghiệp vụ ngoài luồng đăng ký OTP. */
public final class UserTestFixtures {
    private UserTestFixtures() {}

    public static User verifiedUser(String email, String passwordHash, String fullName, Role role) {
        User user = new User(email, passwordHash, fullName, role);
        user.setEmailVerified(true);
        return user;
    }
}
