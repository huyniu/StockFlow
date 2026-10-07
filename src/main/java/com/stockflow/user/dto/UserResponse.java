package com.stockflow.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.user.domain.User;
import java.time.Instant;

/**
 * DTO response an toàn cho user, không bao gồm password hash hay dữ liệu nội bộ nhạy cảm.
 * API luôn trả DTO này thay vì expose trực tiếp JPA entity.
 */
public record UserResponse(
        Long id,
        String email,
        @JsonProperty("full_name") String fullName,
        String phone,
        String role,
        String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("default_address") com.stockflow.user.domain.DefaultAddress defaultAddress) {

    /**
     * Chuyển {@link User} entity thành DTO response để controller không expose trực tiếp entity ra API.
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getName(),
                user.getStatus().name(),
                user.getCreatedAt(), user.getDefaultAddress());
    }
}
