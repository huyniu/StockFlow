package com.stockflow.user.api;

import com.stockflow.user.domain.User;
import com.stockflow.user.dto.UpdateProfileRequest;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final UserProfileService profiles;

    /** Nhận service chỉnh sửa hồ sơ; quyền hạn không bao giờ lấy từ nội dung request. */
    public UserController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * Trả về thông tin user hiện tại được nạp từ JWT trong SecurityContext.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the profile of the current user")
    public UserResponse me(@AuthenticationPrincipal User currentUser) {
        return UserResponse.from(currentUser);
    }

    /** Mọi role đã xác thực chỉ sửa liên hệ của chính mình; không có tham số ID tài khoản đích. */
    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật họ tên và số điện thoại của chính tài khoản đăng nhập")
    public UserResponse updateMe(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.updateProfile(currentUser.getId(), request);
    }
}
