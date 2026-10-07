package com.stockflow.user.service;

import com.stockflow.auth.service.EmailService;
import com.stockflow.common.exception.*;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class PasswordChangeService {
    private final UserRepository users; private final PasswordEncoder encoder; private final EmailService email;
    public PasswordChangeService(UserRepository users,PasswordEncoder encoder,EmailService email) {this.users=users;this.encoder=encoder;this.email=email;}
    @Transactional public void change(Long userId,String current,String next) {
        var user=users.findLockedById(userId).orElseThrow(()->new UnauthorizedException("Vui lòng đăng nhập."));
        if(user.getStatus()!=UserStatus.ACTIVE || !encoder.matches(current,user.getPasswordHash())) throw new BadRequestException("Mật khẩu hiện tại không đúng.");
        if(next.getBytes(StandardCharsets.UTF_8).length>72 || next.length()<6) throw new BadRequestException("Mật khẩu mới phải có ít nhất 6 ký tự và tối đa 72 byte.");
        if(encoder.matches(next,user.getPasswordHash())) throw new BadRequestException("Hãy dùng mật khẩu khác mật khẩu hiện tại.");
        user.setPasswordHash(encoder.encode(next));user.invalidateAccessTokens();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){email.sendPasswordChanged(user.getEmail());}});
    }
}
