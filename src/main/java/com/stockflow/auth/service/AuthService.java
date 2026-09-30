package com.stockflow.auth.service;

import com.stockflow.auth.dto.AuthResponse;
import com.stockflow.auth.dto.LoginRequest;
import com.stockflow.auth.dto.RegisterRequest;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.user.domain.Role;
import com.stockflow.user.domain.User;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.repository.RoleRepository;
import com.stockflow.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service chứa logic đăng ký, đăng nhập, băm mật khẩu và phát JWT.
 * Controller chỉ điều phối HTTP, còn mọi quyết định nghiệp vụ về auth được gom tại đây.
 */
@Service
public class AuthService {

    private static final String DEFAULT_CUSTOMER_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Inject repository, password encoder và JWT provider cần cho luồng authentication.
     */
    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Đăng ký user mới với role CUSTOMER mặc định. Email được chuẩn hóa về chữ thường để tránh trùng tài khoản
     * do khác biệt hoa/thường, và mật khẩu luôn được băm bằng BCrypt trước khi lưu.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Bước 1: Xóa khoảng trắng thừa và đổi email về chữ thường
        // Ví dụ: "  NguyenVanA@GMAIL.COM  " -> "nguyenvana@gmail.com"
        String normalizedEmail = request.email().trim().toLowerCase();
        // Bước 2: Kiểm tra email này đã có ai dùng chưa
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email đã được sử dụng.");
        }
        // Bước 3: Lấy quyền mặc định là "CUSTOMER" trong database ra
        Role customerRole = roleRepository.findByName(DEFAULT_CUSTOMER_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Role CUSTOMER chưa được khởi tạo."));

        // Chỉ lưu password hash, không bao giờ lưu mật khẩu gốc vào database.
        String passwordHash = passwordEncoder.encode(request.password());
        User user = userRepository.save(new User(normalizedEmail, passwordHash, request.fullName().trim(), customerRole));
        return buildAuthResponse(user);
    }

    /**
     * Đăng nhập bằng email/password. Khi sai credential, response luôn giống nhau để không tiết lộ email có tồn tại hay không.
     */
    @Transactional(readOnly = true) // readOnly = true: Báo DB là tôi chỉ đọc dữ liệu, giúp truy vấn nhanh hơn
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        // Bước 1: Đi tìm user có email này trong DB. Nếu không tìm thấy -> Báo lỗi!
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng."));

        // Bước 2: Dùng PasswordEncoder so sánh mật khẩu khách vừa gõ với mật khẩu băm trong DB
        // matches(mật_khẩu_gốc, mật_khẩu_đã_băm)
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng.");
        }
        // Bước 3: Đúng cả email và pass -> In vé (Token) trao cho khách
        return buildAuthResponse(user);
    }

    /**
     * Tạo response chung gồm JWT và thông tin user an toàn, không trả password hash ra ngoài API.
     */
    private AuthResponse buildAuthResponse(User user) {
        // 1. Đưa user vào máy in token -> nhận được chuỗi chữ "eyJhbGciOi..."
        String token = jwtTokenProvider.generateToken(user);
        // 2. Đóng gói token và thông tin cơ bản của user (id, email, tên) trả về
        return AuthResponse.bearer(token, UserResponse.from(user));
    }
}
