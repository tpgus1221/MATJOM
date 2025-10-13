package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReissueServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                       // 기존 액세스 토큰.
    private static final String REFRESH_TOKEN = "refresh-token";                     // 기존 리프레시 토큰.
    private static final String NEW_ACCESS_TOKEN = "new-access";                     // 재발급된 액세스 토큰.
    private static final String NEW_REFRESH_TOKEN = "new-refresh";                   // 재발급된 리프레시 토큰.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001");
    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                        // 토큰 생성기 모킹.
    @Mock
    private UserRepository userRepository;                                            // 사용자 저장소 모킹.
    @Mock
    private RefreshTokenRepository refreshTokenRepository;                            // 리프레시 토큰 저장소 모킹.
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;                        // 블랙리스트 저장소 모킹.

    @InjectMocks
    private ReissueService reissueService;                                            // 테스트 대상 서비스.

    private User user;                                                                // 공통 사용자 엔티티.

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("user@example.com", "User", "encoded");        // Helper: 사용자 엔티티를 만든다.
        setField(user, "id", USER_ID);
    }

    // 정상적으로 재발급되면 새 토큰과 사용자 정보가 반환된다.
    @Test
    void reissue_success() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("REFRESH");
        when(jwtTokenProvider.createAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(TTL);

        LoginResult result = reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(result.getAccessToken()).isEqualTo(NEW_ACCESS_TOKEN);              // 새 액세스 토큰이 담긴다.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo(NEW_REFRESH_TOKEN);             // 새 리프레시 토큰이 담긴다.
        assertThat(result.getResponse().getName()).isEqualTo("User");                 // 응답 이름이 유지된다.
        verify(refreshTokenRepository).save(USER_ID, NEW_REFRESH_TOKEN);              // 새 리프레시 토큰이 저장된다.
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, TTL);                     // 기존 토큰은 블랙리스트에 등록된다.
    }

    // 저장된 리프레시 토큰이 다르면 예외를 던진다.

    @Test
    void reissue_throwsWhenRefreshTokenMismatch() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of("different"));

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(jwtTokenProvider, never()).createAccessToken(user);                    // 새 토큰은 발급되지 않는다.
    }

    // 사용자를 찾지 못하면 인증 실패 예외를 던진다.
    @Test
    void reissue_throwsWhenUserNotFound() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenRepository, never()).find(USER_ID);                        // 리프레시 토큰 조회가 이루어지지 않는다.
    }

    // 토큰 타입이 REFRESH 가 아니면 예외를 던진다.
    @Test
    void reissue_throwsWhenTokenTypeIsNotRefresh() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("ACCESS");

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(jwtTokenProvider, never()).createAccessToken(user);                    // 새 토큰은 발급되지 않는다.
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
