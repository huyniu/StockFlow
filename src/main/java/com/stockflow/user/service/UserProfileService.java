package com.stockflow.user.service;

import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.dto.UpdateProfileRequest;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.repository.UserRepository;
import jakarta.validation.Validator;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cập nhật liên hệ của chính người đăng nhập, giữ nguyên quyền hạn và bản chụp người nhận trên đơn. */
@Service
public class UserProfileService {

    private final UserRepository users;
    private final Validator validator;

    /** Nhận repository và bộ kiểm tra để cả HTTP lẫn lời gọi service đều tuân thủ cùng giới hạn. */
    public UserProfileService(UserRepository users, Validator validator) {
        this.users = users;
        this.validator = validator;
    }

    /**
     * ID phải lấy từ principal tại controller; request không được chọn tài khoản đích.
     * UPDATE giới hạn trường và yêu cầu ACTIVE để không khôi phục quyền đã bị thu hồi trong lúc xử lý.
     */
    @Transactional
    public UserResponse updateProfile(Long currentUserId, UpdateProfileRequest request) {
        if (currentUserId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để cập nhật hồ sơ.");
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BadRequestException("Thông tin hồ sơ không hợp lệ.");
        }
        boolean updateName = request.fullName() != null;
        boolean updatePhone = request.phone() != null;
        if (!updateName && !updatePhone) {
            throw new BadRequestException("Gửi ít nhất họ tên hoặc số điện thoại cần cập nhật.");
        }
        String phone = updatePhone && request.phone().isEmpty() ? null : request.phone();
        int updated = users.updateProfile(
                currentUserId,
                updateName,
                request.fullName(),
                updatePhone,
                phone,
                Instant.now(),
                UserStatus.ACTIVE);
        if (updated != 1) {
            throw new UnauthorizedException("Tài khoản không còn hoạt động. Vui lòng đăng nhập lại.");
        }
        // Nạp lại sau bulk UPDATE; tránh trả thông tin cũ từ principal hoặc persistence context.
        return users.findById(currentUserId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn hoạt động."));
    }
}
