package com.stockflow.user.repository;

import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository truy vấn bảng users, ưu tiên load kèm role cho các luồng authentication.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE users SET default_province_id=:provinceId, default_province_name=:provinceName,
        default_district_id=:districtId, default_district_name=:districtName,
        default_ward_code=:wardCode, default_ward_name=:wardName, default_street_address=:streetAddress
        WHERE id=:userId AND status='ACTIVE'
        """, nativeQuery = true)
    int updateDefaultAddress(@Param("userId") Long userId, @Param("provinceId") Integer provinceId,
        @Param("provinceName") String provinceName, @Param("districtId") Integer districtId,
        @Param("districtName") String districtName, @Param("wardCode") String wardCode,
        @Param("wardName") String wardName, @Param("streetAddress") String streetAddress);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForVerification(@Param("email") String email);

    /**
     * Kiểm tra email đã được sử dụng hay chưa khi register.
     */
    boolean existsByEmail(String email);

    /**
     * Tìm user theo email và load role để tránh lazy loading khi tạo JWT hoặc Authentication.
     */
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    /**
     * Chỉ ghi các trường liên hệ được gửi; không ghi đè email, mật khẩu, role hoặc trạng thái từ entity cũ.
     * Cờ riêng cho từng trường phân biệt bỏ qua với việc xóa số điện thoại bằng chuỗi rỗng.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.fullName = CASE WHEN :updateName = true THEN :fullName ELSE u.fullName END,
                u.phone = CASE WHEN :updatePhone = true THEN :phone ELSE u.phone END,
                u.updatedAt = :updatedAt
            WHERE u.id = :userId
              AND u.status = :activeStatus
            """)
    int updateProfile(
            @Param("userId") Long userId,
            @Param("updateName") boolean updateName,
            @Param("fullName") String fullName,
            @Param("updatePhone") boolean updatePhone,
            @Param("phone") String phone,
            @Param("updatedAt") Instant updatedAt,
            @Param("activeStatus") UserStatus activeStatus);
}
