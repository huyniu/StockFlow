package com.stockflow.auth.security;

import com.stockflow.user.domain.User;
import com.stockflow.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter đọc Bearer token trên mỗi request và nạp {@link org.springframework.security.core.Authentication}
 * vào {@link SecurityContextHolder} khi token hợp lệ.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * Inject JWT provider và repository để xác minh token rồi nạp thông tin người dùng hiện tại từ database.
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    /**
     * Xử lý mỗi request: nếu có Bearer token hợp lệ thì tạo Authentication, nếu không thì bỏ qua để Spring Security
     * xử lý như một request chưa đăng nhập.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null && jwtTokenProvider.validateToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtTokenProvider.extractUsername(token);
            // Chỉ đặt Authentication khi token trỏ tới user còn tồn tại trong database.
            userRepository.findByEmail(email).ifPresent(user -> authenticateRequest(request, user));
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Tách token từ header Authorization theo định dạng chuẩn {@code Bearer <token>}.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    /**
     * Tạo Authentication chứa principal là {@link User} entity và authority dạng {@code ROLE_<role>}.
     * Spring Security dùng danh sách authority này cho các kiểm tra phân quyền theo role.
     */
    private void authenticateRequest(HttpServletRequest request, User user) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
