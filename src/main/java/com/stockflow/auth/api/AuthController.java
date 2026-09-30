package com.stockflow.auth.api;

import com.stockflow.auth.dto.AuthResponse;
import com.stockflow.auth.dto.LoginRequest;
import com.stockflow.auth.dto.RegisterRequest;
import com.stockflow.auth.service.AuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller cho các API công khai liên quan đến đăng ký và đăng nhập.
 * Controller này không chứa logic bảo mật trực tiếp mà ủy quyền cho {@link AuthService}.
 */
@RestController
@Tag(name = "Authentication", description = "Public registration and JWT login")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Inject {@link AuthService} để controller chỉ làm nhiệm vụ nhận request, validate DTO và trả response HTTP.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Đăng ký tài khoản mới và trả về JWT để client có thể gọi các endpoint protected ngay sau khi đăng ký.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a customer account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Đăng nhập bằng email/password và trả về JWT Bearer token nếu credential hợp lệ.
     */
    @PostMapping("/login")
    @Operation(summary = "Log in and receive a Bearer access token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
