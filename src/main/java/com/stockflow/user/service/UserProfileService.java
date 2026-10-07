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
    private final com.stockflow.shipping.client.GhnClient ghn;
    private final AddressBookService addressBook;

    /** Nhận repository và bộ kiểm tra để cả HTTP lẫn lời gọi service đều tuân thủ cùng giới hạn. */
    public UserProfileService(UserRepository users, Validator validator, com.stockflow.shipping.client.GhnClient ghn, AddressBookService addressBook) {
        this.users = users;
        this.validator = validator;
        this.ghn = ghn;
        this.addressBook = addressBook;
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
        boolean updateAddress = request.defaultAddress() != null || Boolean.TRUE.equals(request.clearDefaultAddress());
        if (!updateName && !updatePhone && !updateAddress) {
            throw new BadRequestException("Gửi ít nhất họ tên hoặc số điện thoại cần cập nhật.");
        }
        com.stockflow.user.domain.DefaultAddress address = null;
        if (request.defaultAddress() != null) {
            if (Boolean.TRUE.equals(request.clearDefaultAddress())) throw new BadRequestException("Không vừa lưu vừa xóa địa chỉ.");
            var input = request.defaultAddress();
            var province = ghn.getProvinces().stream().filter(p -> p.id() == input.provinceId()).findFirst()
                    .orElseThrow(() -> new BadRequestException("Tỉnh/thành không hợp lệ."));
            var district = ghn.getDistricts(input.provinceId()).stream().filter(d -> d.id() == input.districtId()).findFirst()
                    .orElseThrow(() -> new BadRequestException("Quận/huyện không thuộc tỉnh/thành đã chọn."));
            var ward = ghn.getWards(input.districtId()).stream().filter(w -> w.code().equals(input.wardCode())).findFirst()
                    .orElseThrow(() -> new BadRequestException("Phường/xã không thuộc quận/huyện đã chọn."));
            address = new com.stockflow.user.domain.DefaultAddress(province.id(), province.name(), district.id(), district.name(),
                    ward.code(), ward.name(), input.streetAddress());
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
        if (updateAddress) {
            int addressUpdated = users.updateDefaultAddress(currentUserId,
                address == null ? null : address.provinceId, address == null ? null : address.provinceName,
                address == null ? null : address.districtId, address == null ? null : address.districtName,
                address == null ? null : address.wardCode, address == null ? null : address.wardName,
                address == null ? null : address.streetAddress);
            if (addressUpdated != 1) throw new UnauthorizedException("Tài khoản không còn hoạt động.");
            addressBook.synchronizeLegacy(currentUserId, address);
        }
        // Nạp lại sau bulk UPDATE; tránh trả thông tin cũ từ principal hoặc persistence context.
        return users.findById(currentUserId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn hoạt động."));
    }
}
