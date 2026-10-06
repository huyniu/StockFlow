package com.stockflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtAuthenticationFilter;
import com.stockflow.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình bảo mật trung tâm cho API StockFlow. Lớp này định nghĩa endpoint nào được truy cập công khai,
 * endpoint nào cần JWT hợp lệ, cách mã hóa mật khẩu và cách trả lỗi bảo mật theo JSON thống nhất.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Tạo {@link PasswordEncoder} dùng thuật toán BCrypt để băm mật khẩu trước khi lưu vào database.
     * BCrypt tự sinh salt và có chi phí tính toán đủ cao cho luồng đăng ký/đăng nhập thông thường.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Khai báo {@link UserDetailsService} tối thiểu để tắt user/password mặc định của Spring Security.
     * Luồng đăng nhập thật của dự án đi qua {@code AuthService}, còn request đã đăng nhập được xác thực bằng JWT filter.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Không hỗ trợ đăng nhập bằng UserDetailsService mặc định.");
        };
    }

    /**
     * Cấu hình filter chain stateless cho REST API: tắt session, tắt form login, gắn JWT filter trước
     * {@link UsernamePasswordAuthenticationFilter} và chuẩn hóa lỗi 401/403 thành JSON.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // API dùng JWT stateless nên không dựa vào cookie session; tắt CSRF để client không cần CSRF token.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Giao diện tĩnh được mở công khai; API vẫn kiểm tra JWT và quyền ở controller/service.
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.ico", "/assets/**").permitAll()
                        // Trang chi tiết chỉ mở GET công khai; API ghi catalog vẫn yêu cầu ADMIN.
                        .requestMatchers(HttpMethod.GET, "/san-pham/*").permitAll()
                        // Cho phép đọc tài liệu công khai; các API nghiệp vụ vẫn giữ kiểm tra JWT và role.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        // Chỉ đọc hãng được công khai; tạo hãng vẫn kiểm tra JWT và ROLE_ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/v1/brands/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // Khách vãng lai chỉ nhận lựa chọn chi nhánh tối thiểu; không mở API kho vận hành.
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefront/branches").permitAll()
                        // Chỉ public catalog bán chạy; báo cáo doanh thu và lịch sử kho vẫn giữ quyền nội bộ.
                        .requestMatchers(HttpMethod.GET, "/api/v1/storefront/bestsellers").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/return").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/locations/provinces", "/api/v1/locations/districts", "/api/v1/locations/wards").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/locations/calculate-fee").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // JWT filter phải chạy trước filter username/password để SecurityContext có principal từ Bearer token.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Trả về lỗi 401 dạng JSON khi request không có token hoặc token không hợp lệ.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeSecurityError(
                objectMapper,
                response,
                HttpStatus.UNAUTHORIZED,
                "Yêu cầu cần đăng nhập hợp lệ.",
                request.getRequestURI());
    }

    /**
     * Trả về lỗi 403 dạng JSON khi người dùng đã xác thực nhưng không có quyền truy cập tài nguyên.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeSecurityError(
                objectMapper,
                response,
                HttpStatus.FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.",
                request.getRequestURI());
    }

    /**
     * Ghi response lỗi bảo mật theo cùng format với {@link com.stockflow.common.exception.GlobalExceptionHandler}.
     * Hàm này được dùng ngoài controller nên phải tự set HTTP status, content type và serialize body.
     */
    private void writeSecurityError(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            HttpStatus status,
            String message,
            String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, null));
    }
}
