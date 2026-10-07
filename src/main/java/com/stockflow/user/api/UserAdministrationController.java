package com.stockflow.user.api;

import com.stockflow.common.dto.PageResponse;
import com.stockflow.user.domain.*;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.service.UserAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdministrationController {
    private final UserAdministrationService service;
    public UserAdministrationController(UserAdministrationService service) { this.service=service; }
    @GetMapping
    public PageResponse<UserResponse> list(@AuthenticationPrincipal User actor,
            @RequestParam(required=false) String q, @RequestParam(required=false) String role,
            @RequestParam(required=false) UserStatus status, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return PageResponse.from(service.list(actor.getId(),q,role,status,page,size));
    }
    @GetMapping("/{id}")
    public UserResponse get(@AuthenticationPrincipal User actor, @PathVariable Long id) { return service.get(actor.getId(),id); }
    public record StatusRequest(@NotNull UserStatus status) {}
    @GetMapping("/{id}/permissions")
    public UserAdministrationService.Permissions permissions(@AuthenticationPrincipal User actor,@PathVariable Long id) { return service.permissions(actor.getId(),id); }
    @PatchMapping("/{id}/role")
    public UserAdministrationService.Permissions role(@AuthenticationPrincipal User actor,@PathVariable Long id,
            @Valid @RequestBody com.stockflow.user.dto.UpdateUserRoleRequest request) { return service.changeRole(actor.getId(),id,request); }
    @GetMapping("/{id}/role-history")
    public java.util.List<UserAdministrationService.RoleAudit> history(@AuthenticationPrincipal User actor,@PathVariable Long id) { return service.roleHistory(actor.getId(),id); }
    @PatchMapping("/{id}/status")
    public UserResponse status(@AuthenticationPrincipal User actor, @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(actor.getId(),id,request.status());
    }
}
