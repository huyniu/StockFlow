package com.stockflow.auth.service;

import com.stockflow.auth.dto.AuthResponse;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.auth.security.DemoAccountPolicy;
import com.stockflow.common.exception.*;
import com.stockflow.user.domain.*;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.repository.*;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAuthService {
    private final GoogleTokenVerifier verifier;
    private final GoogleLoginNonce nonces;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider jwt;
    public GoogleAuthService(GoogleTokenVerifier verifier,GoogleLoginNonce nonces,UserRepository users,
            RoleRepository roles,PasswordEncoder passwords,JwtTokenProvider jwt) {
        this.verifier=verifier; this.nonces=nonces; this.users=users; this.roles=roles; this.passwords=passwords; this.jwt=jwt;
    }
    @Transactional
    public AuthResponse login(String credential,String nonce) {
        var identity = verifier.verify(credential,nonce);
        nonces.consume(nonce);
        String subject=identity.getSubject();
        String email=identity.getClaimAsString("email").trim().toLowerCase(Locale.ROOT);
        if (DemoAccountPolicy.isPublicOperatorEmail(email)) throw new UnauthorizedException("Tài khoản không thể đăng nhập.");
        User user=users.findByGoogleSubject(subject).orElse(null);
        if (user != null) user=users.findLockedById(user.getId()).orElseThrow();
        if (user == null) {
            user=users.findByEmailForVerification(email).orElse(null);
            if (user != null) {
                // Google is authoritative for Gmail/Workspace, not arbitrary third-party email addresses.
                String domain=identity.getClaimAsString("hd");
                boolean authoritative=email.endsWith("@gmail.com") || (domain != null && !domain.isBlank() && email.endsWith("@"+domain.toLowerCase(Locale.ROOT)));
                if (!authoritative || !user.isEmailVerified() || (user.getGoogleSubject()!=null && !subject.equals(user.getGoogleSubject()))) {
                    throw new ConflictException("Email đã có tài khoản StockFlow. Vui lòng đăng nhập bằng mật khẩu hiện tại.");
                }
            } else {
                String name=identity.getClaimAsString("name");
                if (name==null || name.isBlank()) name=email.substring(0,email.indexOf('@'));
                name=name.replaceAll("[\\p{Cntrl}]","").strip();
                if (name.isEmpty()) name="Khách hàng Google";
                if (name.length()>150) name=name.substring(0,150);
                user=new User(email,passwords.encode(UUID.randomUUID().toString()),name,roles.findByName("CUSTOMER").orElseThrow());
                user.setEmailVerified(true);
            }
            if (user.getStatus()!=UserStatus.ACTIVE) throw new UnauthorizedException("Tài khoản không thể đăng nhập.");
            user.setGoogleSubject(subject);
            users.saveAndFlush(user);
        }
        if (user.getStatus()!=UserStatus.ACTIVE || !user.isEmailVerified() || DemoAccountPolicy.isPublicOperator(user)) throw new UnauthorizedException("Tài khoản không thể đăng nhập.");
        return AuthResponse.bearer(jwt.generateToken(user),UserResponse.from(user));
    }
}
