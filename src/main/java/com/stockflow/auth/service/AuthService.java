package com.stockflow.auth.service;

import com.stockflow.auth.dto.AuthResponse;
import com.stockflow.auth.dto.LoginRequest;
import com.stockflow.auth.dto.RegisterRequest;
import com.stockflow.auth.dto.RegistrationResponse;
import com.stockflow.auth.dto.VerifyEmailRequest;
import com.stockflow.auth.domain.EmailVerificationToken;
import com.stockflow.auth.repository.EmailVerificationTokenRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.EmailNotVerifiedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.user.domain.Role;
import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
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
    private final EmailVerificationTokenRepository verificationTokens;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    /**
     * Inject repository, password encoder và JWT provider cần cho luồng authentication.
     */
    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            EmailVerificationTokenRepository verificationTokens,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.verificationTokens = verificationTokens;
        this.emailService = emailService;
    }

    /**
     * Đăng ký user mới với role CUSTOMER mặc định. Email được chuẩn hóa về chữ thường để tránh trùng tài khoản
     * do khác biệt hoa/thường, và mật khẩu luôn được băm bằng BCrypt trước khi lưu.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        // Bước 1: Xóa khoảng trắng thừa và đổi email về chữ thường
        String normalizedEmail = normalizeEmail(request.email());
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
        issueOtp(user, Instant.now());
        return RegistrationResponse.pending(normalizedEmail);
    }

    /**
     * Đăng nhập bằng email/password và chỉ phát JWT cho tài khoản ACTIVE đã xác thực email.
     * Sai mật khẩu hoặc tài khoản bị khóa trả cùng thông điệp để không tiết lộ trạng thái tài khoản.
     */
    @Transactional(readOnly = true) // readOnly = true: Báo DB là tôi chỉ đọc dữ liệu, giúp truy vấn nhanh hơn
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        // Bước 1: Đi tìm user có email này trong DB. Nếu không tìm thấy -> Báo lỗi!
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng."));

        // Bước 2: Dùng PasswordEncoder so sánh mật khẩu khách vừa gõ với mật khẩu băm trong DB
        // matches(mật_khẩu_gốc, mật_khẩu_đã_băm)
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Email hoặc mật khẩu không đúng.");
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(user.getEmail());
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = pendingUser(request.email());
        Instant now = Instant.now();
        EmailVerificationToken token = verificationTokens.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .orElseThrow(() -> new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn."));
        // Chỉ mã mới nhất có hiệu lực; khóa user ngăn verify/resend đồng thời.
        if (token.getVerifiedAt() != null || !token.getExpiresAt().isAfter(now)
                || !token.getOtpCode().equals(request.otp())) {
            throw new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn.");
        }
        token.markVerified(now);
        user.setEmailVerified(true);
        return buildAuthResponse(user);
    }

    @Transactional
    public RegistrationResponse resendOtp(String email) {
        User user = pendingUser(email);
        Instant now = Instant.now();
        verificationTokens.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId()).ifPresent(token -> {
            if (token.getCreatedAt().plusSeconds(60).isAfter(now)) {
                throw new com.stockflow.common.exception.AppException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Vui lòng chờ 60 giây giữa hai lần gửi mã OTP.");
            }
        });
        issueOtp(user, now);
        return RegistrationResponse.pending(user.getEmail());
    }

    private User pendingUser(String email) {
        User user = userRepository.findByEmailForVerification(normalizeEmail(email))
                .orElseThrow(() -> new BadRequestException("Không thể xác thực email này."));
        if (user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Không thể xác thực email này.");
        }
        return user;
    }

    private void issueOtp(User user, Instant now) {
        String previous = verificationTokens.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .map(EmailVerificationToken::getOtpCode).orElse(null);
        String candidate;
        do {
            candidate = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (candidate.equals(previous));
        String otp = candidate;
        verificationTokens.save(new EmailVerificationToken(user, otp, now));
        String email = user.getEmail();
        // Không gửi OTP cho transaction đã rollback.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { emailService.sendVerificationOtp(email, otp); }
        });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
