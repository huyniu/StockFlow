package com.stockflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationResponse(String email,
        @JsonProperty("requires_verification") boolean requiresVerification, String message) {
    public static RegistrationResponse pending(String email) {
        return new RegistrationResponse(email, true, "Vui lòng nhập mã OTP để kích hoạt tài khoản.");
    }
}
