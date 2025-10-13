package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "user@example.com";                          // 테스트용 이메일.
    private static final String NAME = "User";                                       // 테스트용 이름.
    private static final String RAW_PASSWORD = "password123";                        // 입력 비밀번호.
    private static final String ENCODED_PASSWORD = "encoded-password";               // 암호화된 비밀번호.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;                                            // 사용자 저장소 모킹.

    @Mock
    private PasswordEncoder passwordEncoder;                                          // 비밀번호 인코더 모킹.

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                        // 토큰 생성기 모킹.

    @Mock
    private RefreshTokenRepository refreshTokenRepository;                            // 리프레시 토큰 저장소 모킹.

    @Mock
    private LoginRateLimiter loginRateLimiter;                                        // 로그인 시도 제한기 모킹.

    @InjectMocks
    private LoginService loginService;                                                // 테스트 대상 서비스.

    private User activeUser;                                                          // 정상 사용자 엔티티.

    @BeforeEach
    void setUp() {
        activeUser = createUser();                                                    // 로그인에 사용할 사용자를 만든다.
    }

    // 로그인에 성공하면 새 토큰을 발급하고 정보를 반환한다.
    @Test
    void login_success() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(activeUser)).thenReturn(REFRESH_TOKEN);

        LoginResult result = loginService.login(request);

        assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);                  // 액세스 토큰이 반환된다.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo(REFRESH_TOKEN);  // 리프레시 토큰이 함께 반환된다.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                  // 응답 본문에 사용자 이름이 담긴다.
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);             // 비밀번호 검증이 수행된다.
        verify(refreshTokenRepository).save(USER_ID, REFRESH_TOKEN);                 // 리프레시 토큰이 저장된다.
        verify(loginRateLimiter).reset(EMAIL);                                       // 실패 횟수가 초기화된다.
        verify(loginRateLimiter).isLimitReached(EMAIL);                              // 차단 여부를 조회한다.
    }

    // 이미 차단된 경우 즉시 예외를 던진다.

    @Test
    void login_blocked_whenLimitAlreadyReached() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(true);
        when(loginRateLimiter.getRemainingSeconds(EMAIL)).thenReturn(180L);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_TOO_MANY_ATTEMPTS); // 차단 예외 코드가 반환된다.
        assertThat(exception.getMessage()).contains("180");                             // 메시지에 남은 시간이 포함된다.
        verify(loginRateLimiter).isLimitReached(EMAIL);                               // 차단 상태를 조회한다.
        verify(loginRateLimiter).getRemainingSeconds(EMAIL);                          // 남은 대기 시간을 조회한다.
        verifyNoInteractions(userRepository);                                         // 사용자 조회는 이루어지지 않는다.
        verify(loginRateLimiter, never()).recordFailure(EMAIL);                       // 실패 횟수는 증가하지 않는다.
        verify(loginRateLimiter, never()).reset(EMAIL);                               // 초기화도 수행되지 않는다.
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    // 사용자가 존재하지 않으면 실패를 기록하고 예외를 던진다.
    @Test
    void login_userNotFound_recordsFailure() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS); // 인증 실패 코드가 반환된다.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // 실패 횟수를 기록한다.
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // 차단 여부를 두 번 확인한다.
        verify(loginRateLimiter, never()).getRemainingSeconds(EMAIL);                 // 남은 시간을 조회하지 않는다.
        verify(loginRateLimiter, never()).reset(EMAIL);                               // 초기화도 수행되지 않는다.
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    // 비밀번호가 틀리면 실패를 기록한다.
    @Test
    void login_invalidPassword_recordsFailure() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS); // 인증 실패 코드가 반환된다.
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);              // 비밀번호 검증이 시도된다.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // 실패 횟수가 증가한다..
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // 차단 여부를 두 번 확인한다.
        verify(loginRateLimiter, never()).getRemainingSeconds(EMAIL);                 // 남은 시간은 조회하지 않는다.
        verify(loginRateLimiter, never()).reset(EMAIL);                               // 초기화도 수행되지 않는다.
        verifyNoInteractions(refreshTokenRepository);
    }

    // 비밀번호가 반복해서 틀리면 차단 예외를 던진다.

    @Test
    void login_invalidPassword_triggersLimitExceededResponse() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, true);
        when(loginRateLimiter.getRemainingSeconds(EMAIL)).thenReturn(120L);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_TOO_MANY_ATTEMPTS); // 차단 코드가 반환된다.
        assertThat(exception.getMessage()).contains("120");                             // 메시지에 남은 시간이 포함된다.
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);              // 비밀번호 검증이 시도된다.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // 실패 횟수를 기록한다.
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // 차단 여부를 두 번 확인한다.
        verify(loginRateLimiter).getRemainingSeconds(EMAIL);                          // 남은 시간을 조회한다.
        verify(loginRateLimiter, never()).reset(EMAIL);                               // 초기화는 수행되지 않는다.

        verifyNoInteractions(refreshTokenRepository);
    }

    private LoginRequest createRequest() {

        LoginRequest request = new LoginRequest();                                    // Helper: 로그인 요청을 생성한다.

        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    private User createUser() {
        User user = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);              // Helper: 사용자 엔티티를 생성한다.
        setField(user, "id", USER_ID);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
