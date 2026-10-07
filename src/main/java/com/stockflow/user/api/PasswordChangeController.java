package com.stockflow.user.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.user.domain.User;
import com.stockflow.user.service.PasswordChangeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordChangeController {
    public record Request(@JsonProperty("current_password") @NotBlank @Size(max=100) String currentPassword,
            @JsonProperty("new_password") @NotBlank @Size(min=6,max=72) String newPassword) {}
    private final PasswordChangeService service;
    public PasswordChangeController(PasswordChangeService service) { this.service=service; }
    @PostMapping("/api/v1/users/me/password") public Map<String,String> change(@AuthenticationPrincipal User user,@Valid @RequestBody Request body) {
        service.change(user.getId(),body.currentPassword(),body.newPassword());
        return Map.of("message","Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }
}
