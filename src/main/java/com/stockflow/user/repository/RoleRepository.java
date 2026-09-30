package com.stockflow.user.repository;

import com.stockflow.user.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy vấn bảng roles để gán vai trò cho user mới và xây dựng authority.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Tìm role theo tên duy nhất, ví dụ CUSTOMER.
     */
    Optional<Role> findByName(String name);
}
