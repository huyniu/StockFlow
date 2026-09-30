package com.stockflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.user.dto.UserResponse;

/**
 * DTO response cho API đăng ký và đăng nhập, gồm access token và thông tin user an toàn.
 * Tên field dùng snake_case để khớp convention HTTP API.
 */
public record AuthResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        UserResponse user) {

    /**
     * Tạo response Bearer token theo convention của header Authorization.
     */
    public static AuthResponse bearer(String accessToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", user);
    }
}
