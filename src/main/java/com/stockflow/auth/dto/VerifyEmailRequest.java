package com.stockflow.auth.dto;

import jakarta.validation.constraints.*;

public record VerifyEmailRequest(@NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "OTP phải gồm 6 chữ số.") String otp) {}
