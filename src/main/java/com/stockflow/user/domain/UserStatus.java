package com.stockflow.user.domain;

/**
 * Trạng thái tài khoản user, ánh xạ constraint users_status_check trong database.
 */
public enum UserStatus {
    /**
     * Tài khoản đang hoạt động và được phép đăng nhập.
     */
    ACTIVE,

    /**
     * Tài khoản bị vô hiệu hóa, không được đăng nhập hoặc xác thực bằng JWT đã phát.
     */
    INACTIVE
}
