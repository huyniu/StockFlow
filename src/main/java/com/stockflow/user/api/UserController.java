package com.stockflow.user.api;

import com.stockflow.user.domain.User;
import com.stockflow.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller cho các API thông tin cá nhân của người dùng đã đăng nhập.
 * Các endpoint trong controller này phụ thuộc vào principal được nạp từ JWT trong SecurityContext.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * Trả về thông tin user hiện tại được nạp từ JWT trong SecurityContext.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User currentUser) {
        return UserResponse.from(currentUser);
    }
}
