package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.rate.LoginRateLimiter;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String TOO_MANY_ATTEMPTS_MESSAGE = "5회 연속 로그인이 실패했습니다. %d초 뒤에 다시 시도해주세요.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginRateLimiter loginRateLimiter;

    public LoginResult login(LoginRequest request) {
        String email = request.getEmail();

        // 차단 중이면 남은 대기 시간을 안내하고 요청을 종료한다.
        if (loginRateLimiter.isLimitReached(email)) {
            throw tooManyAttempts(email);
        }

        User user = userRepository.findByEmailAndProvider(email, AuthProvider.LOCAL)
                .orElseThrow(() -> invalidCredentials(email));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials(email);
        }

        return issueTokens(user);
    }

    // 액세스/리프레시 토큰을 발급한다.
    public LoginResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenRepository.save(user.getId(), refreshToken);
        loginRateLimiter.reset(user.getEmail());

        LoginResponse response = LoginResponse.from(user, refreshToken);
        return LoginResult.from(accessToken, response);
    }

    private AuthException invalidCredentials(String email) {
        // 해당 이메일의 실패 횟수를 기록한다.
        loginRateLimiter.recordFailure(email);

        if (loginRateLimiter.isLimitReached(email)) {
            return tooManyAttempts(email);
        }
        return new AuthException(ErrorCode.INVALID_CREDENTIALS);
    }

    // 남은 대기 시간을 포함한 예외를 생성한다.
    private AuthException tooManyAttempts(String email) {
        long remainingSeconds = loginRateLimiter.getRemainingSeconds(email);
        String message = String.format(TOO_MANY_ATTEMPTS_MESSAGE, remainingSeconds);
        return new AuthException(ErrorCode.LOGIN_TOO_MANY_ATTEMPTS, message);
    }
}
