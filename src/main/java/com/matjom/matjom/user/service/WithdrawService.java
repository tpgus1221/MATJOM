package com.matjom.matjom.user.service;

import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.dto.WithdrawRequest;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.DeletedUser;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.DeletedUserRepository;
import com.matjom.matjom.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final UserRepository userRepository;
    private final DeletedUserRepository deletedUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final LogoutService logoutService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void withdraw(String accessToken, WithdrawRequest request) {
        UUID userId = jwtTokenProvider.getUserId(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        //로컬 유저인 경우 비밀번호 검증
        if (user.getProvider() == AuthProvider.LOCAL) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new AuthException(ErrorCode.PASSWORD_MISMATCH);
            }
        }

        DeletedUser deletedUser = DeletedUser.from(user, OffsetDateTime.now());
        deletedUserRepository.save(deletedUser);

        userRepository.delete(user);

        logoutService.logoutHelper(userId, accessToken);
    }
}
