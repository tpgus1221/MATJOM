package com.matjom.matjom.user.service;

import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.dto.ChangePasswordRequest;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LogoutService logoutService;

    @Transactional
    public void changePassword(String accessToken, ChangePasswordRequest request) {
        UUID userId = jwtTokenProvider.getUserId(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        if (!user.isLocalAccount()) {
            throw new AuthException(ErrorCode.OAUTH_PASSWORD_CHANGE_NOT_ALLOWED);
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AuthException(ErrorCode.PASSWORD_MISMATCH);
        }

        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.changePassword(encodedPassword);
        userRepository.save(user);

        logoutService.logoutHelper(userId, accessToken);
    }
}