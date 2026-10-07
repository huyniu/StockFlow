package com.stockflow.user.service;

import com.stockflow.common.exception.*;
import com.stockflow.user.domain.*;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {
    private final UserRepository users;
    private final com.stockflow.user.repository.RoleRepository roles;
    private final com.stockflow.warehouse.repository.WarehouseRepository warehouses;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final jakarta.validation.Validator validator;
    public UserAdministrationService(UserRepository users, com.stockflow.user.repository.RoleRepository roles,
            com.stockflow.warehouse.repository.WarehouseRepository warehouses,
            org.springframework.jdbc.core.JdbcTemplate jdbc, jakarta.validation.Validator validator) {
        this.users = users; this.roles = roles; this.warehouses = warehouses; this.jdbc = jdbc; this.validator = validator;
    }
    private void requireAdmin(Long actorId) {
        User actor = users.findById(actorId).orElseThrow(() -> new ForbiddenException("Không có quyền quản lý tài khoản."));
        if (actor.getStatus() != UserStatus.ACTIVE || !"ADMIN".equals(actor.getRole().getName())) {
            throw new ForbiddenException("Chỉ Admin được quản lý tài khoản.");
        }
    }
    @Transactional(readOnly=true)
    public Page<UserResponse> list(Long actorId, String q, String role, UserStatus status, int page, int size) {
        requireAdmin(actorId);
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 200)) throw new BadRequestException("Bộ lọc tài khoản không hợp lệ.");
        if (role != null && !role.isBlank() && !java.util.Set.of("CUSTOMER","WAREHOUSE_STAFF","MANAGER","ADMIN").contains(role)) throw new BadRequestException("Vai trò không hợp lệ.");
        return users.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (q != null && !q.isBlank()) {
                String term = "%" + q.strip().toLowerCase(java.util.Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("fullName")),term,'\\'), cb.like(cb.lower(root.get("email")),term,'\\'), cb.like(root.get("phone"),term,'\\')));
            }
            if (role != null && !role.isBlank()) predicates.add(cb.equal(root.get("role").get("name"), role));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,"id"))).map(UserResponse::from);
    }
    @Transactional(readOnly=true)
    public UserResponse get(Long actorId, Long id) {
        requireAdmin(actorId);
        return UserResponse.from(users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản.")));
    }
    @Transactional
    public UserResponse changeStatus(Long actorId, Long id, UserStatus status) {
        requireAdmin(actorId);
        if (status == null) throw new BadRequestException("Vui lòng chọn trạng thái.");
        User target = users.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
        if (actorId.equals(id) || "ADMIN".equals(target.getRole().getName()) || target.getEmail().endsWith("@stockflow.invalid")) {
            throw new ConflictException("Không thay đổi trạng thái tài khoản Admin hoặc tài khoản hệ thống.");
        }
        target.setStatus(status);
        return UserResponse.from(target);
    }

    public record Permissions(String role, @com.fasterxml.jackson.annotation.JsonProperty("warehouse_ids") java.util.List<Long> warehouseIds) {}
    public record RoleAudit(Long id, @com.fasterxml.jackson.annotation.JsonProperty("actor_id") Long actorId,
        @com.fasterxml.jackson.annotation.JsonProperty("old_role") String oldRole,
        @com.fasterxml.jackson.annotation.JsonProperty("new_role") String newRole,
        @com.fasterxml.jackson.annotation.JsonProperty("old_warehouse_ids") java.util.List<Long> oldWarehouseIds,
        @com.fasterxml.jackson.annotation.JsonProperty("new_warehouse_ids") java.util.List<Long> newWarehouseIds,
        @com.fasterxml.jackson.annotation.JsonProperty("created_at") java.time.Instant createdAt) {}

    private java.util.List<Long> assignments(Long id) {
        return jdbc.queryForList("SELECT warehouse_id FROM warehouse_staff_assignments WHERE user_id=? ORDER BY warehouse_id", Long.class,id);
    }
    @Transactional(readOnly=true)
    public Permissions permissions(Long actorId, Long id) {
        UserResponse user = get(actorId,id);
        return new Permissions(user.role(),assignments(id));
    }
    @Transactional
    public Permissions changeRole(Long actorId, Long id, com.stockflow.user.dto.UpdateUserRoleRequest request) {
        requireAdmin(actorId);
        if (request == null || !validator.validate(request).isEmpty()) throw new BadRequestException("Vai trò hoặc phân công kho không hợp lệ.");
        User target = users.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
        if (actorId.equals(id) || "ADMIN".equals(target.getRole().getName()) || target.getEmail().endsWith("@stockflow.invalid")) throw new ConflictException("Không đổi quyền tài khoản Admin hoặc hệ thống.");
        var selected = request.warehouseIds().stream().distinct().sorted().toList();
        if (selected.size() != request.warehouseIds().size()) throw new BadRequestException("Kho được chọn bị trùng.");
        boolean staff = "WAREHOUSE_STAFF".equals(request.role());
        if (staff == selected.isEmpty()) throw new BadRequestException(staff ? "Nhân viên kho phải được phân công ít nhất một kho." : "Chỉ nhân viên kho được phân công kho.");
        for (Long warehouseId : selected) {
            var warehouse = warehouses.findById(warehouseId).orElseThrow(() -> new BadRequestException("Kho không tồn tại."));
            if (warehouse.getStatus() != com.stockflow.warehouse.domain.WarehouseStatus.ACTIVE) throw new BadRequestException("Chỉ phân công kho đang hoạt động.");
        }
        var before = assignments(id);
        String oldRole = target.getRole().getName();
        if (oldRole.equals(request.role()) && before.equals(selected)) return new Permissions(oldRole,before);
        target.setRole(roles.findByName(request.role()).orElseThrow(() -> new BadRequestException("Vai trò không tồn tại.")));
        users.flush();
        jdbc.update("DELETE FROM warehouse_staff_assignments WHERE user_id=?",id);
        for (Long warehouseId : selected) jdbc.update("INSERT INTO warehouse_staff_assignments(user_id,warehouse_id) VALUES (?,?)",id,warehouseId);
        jdbc.update("INSERT INTO user_role_audit(target_user_id,actor_user_id,old_role,new_role,old_warehouse_ids,new_warehouse_ids) VALUES (?,?,?,?,?,?)",id,actorId,oldRole,request.role(),before.toString(),selected.toString());
        return new Permissions(request.role(),selected);
    }
    private static java.util.List<Long> parseIds(String value) {
        String inner = value.substring(1,value.length()-1).trim();
        return inner.isEmpty() ? java.util.List.of() : java.util.Arrays.stream(inner.split(",")).map(String::trim).map(Long::valueOf).toList();
    }
    @Transactional(readOnly=true)
    public java.util.List<RoleAudit> roleHistory(Long actorId, Long id) {
        get(actorId,id);
        return jdbc.query("SELECT * FROM user_role_audit WHERE target_user_id=? ORDER BY id DESC LIMIT 20",
            (row,index) -> new RoleAudit(row.getLong("id"),row.getLong("actor_user_id"),row.getString("old_role"),row.getString("new_role"),
                parseIds(row.getString("old_warehouse_ids")),parseIds(row.getString("new_warehouse_ids")),row.getTimestamp("created_at").toInstant()),id);
    }
}
