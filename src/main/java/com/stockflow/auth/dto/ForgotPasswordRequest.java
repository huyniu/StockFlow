package com.stockflow.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}
