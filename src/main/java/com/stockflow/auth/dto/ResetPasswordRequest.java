package com.stockflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
        @JsonProperty("new_password") @NotBlank @Size(min = 6, max = 72) String newPassword) {}
