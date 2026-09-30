package com.stockflow.user.repository;

import com.stockflow.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository truy vấn bảng users, ưu tiên load kèm role cho các luồng authentication.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Kiểm tra email đã được sử dụng hay chưa khi register.
     */
    boolean existsByEmail(String email);

    /**
     * Tìm user theo email và load role để tránh lazy loading khi tạo JWT hoặc Authentication.
     */
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);
}
