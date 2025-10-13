package com.matjom.matjom.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                      // 테스트용 액세스 토큰.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440000"); // 사용자 ID.
    private static final Duration TTL = Duration.ofMinutes(5);                       // 토큰 잔여 시간.

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                       // JWT 파서 모킹.
    @Mock
    private RefreshTokenRepository refreshTokenRepository;                           // 리프레시 토큰 저장소 모킹.
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;                       // 블랙리스트 저장소 모킹.

    @InjectMocks
    private LogoutService logoutService;                                             // 테스트 대상 서비스.

    // 로그아웃 시 리프레시 토큰을 삭제하고 액세스 토큰을 블랙리스트에 등록한다.
    @Test
    void logout_deletesRefreshTokenAndBlacklistsAccessToken() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(TTL);    // 남은 TTL 값을 제공한다.

        // When
        assertDoesNotThrow(() -> logoutService.logout(ACCESS_TOKEN));                 // 로그아웃을 수행한다.

        // Then
        verify(refreshTokenRepository).delete(USER_ID);                               // 리프레시 토큰이 삭제된다.
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, TTL);                     // 액세스 토큰이 블랙리스트에 저장된다.
    }

    // 로그아웃 헬퍼는 전달된 사용자 ID와 토큰을 그대로 사용한다.
    @Test
    void logoutHelper_appliesProvidedUserIdAndToken() {
        // Given
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(TTL);    // TTL 조회를 모킹한다.

        // When
        logoutService.logoutHelper(USER_ID, ACCESS_TOKEN);                            // 헬퍼를 직접 호출한다.

        // Then
        verify(refreshTokenRepository).delete(USER_ID);                               // 전달된 사용자 ID의 리프레시 토큰이 삭제된다.
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, TTL);                     // 전달된 토큰이 블랙리스트에 저장된다.
    }
}