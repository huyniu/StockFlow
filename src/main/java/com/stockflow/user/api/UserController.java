package com.stockflow.user.api;

import com.stockflow.user.domain.User;
import com.stockflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller cho các API thông tin cá nhân của người dùng đã đăng nhập.
 * Các endpoint trong controller này phụ thuộc vào principal được nạp từ JWT trong SecurityContext.
 */
@RestController
@Tag(name = "Users", description = "Current authenticated user")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * Trả về thông tin user hiện tại được nạp từ JWT trong SecurityContext.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the profile of the current user")
    public UserResponse me(@AuthenticationPrincipal User currentUser) {
        return UserResponse.from(currentUser);
    }
}
