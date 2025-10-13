package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.oauth.GoogleOAuthClient;
import com.matjom.matjom.auth.oauth.GoogleOAuthProfile;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    private static final String ID_TOKEN = "id-token";                                // 테스트용 ID 토큰.
    private static final String EMAIL = "user@example.com";                           // 테스트용 이메일.
    private static final String NAME = "User";                                        // 테스트용 이름.

    @Mock
    private GoogleOAuthClient googleOAuthClient;                                      // Google 토큰 검증기 모킹.
    @Mock
    private UserRepository userRepository;                                            // 사용자 저장소 모킹.
    @Mock
    private LoginService loginService;                                                // 로그인 서비스 모킹.

    @InjectMocks
    private GoogleOAuthService googleOAuthService;                                    // 테스트 대상 서비스.

    private GoogleOAuthProfile profile;                                               // 검증 결과 프로필.
    private GoogleOAuthRequest request;                                               // OAuth 요청 DTO.
    private LoginResult loginResult;                                                  // 토큰 발급 결과.

    @BeforeEach
    void setUp() {
        profile = GoogleOAuthProfile.builder()
                .email(EMAIL)
                .name(NAME)
                .subject("sub")
                .picture("pic")
                .build();

        request = new GoogleOAuthRequest();
        request.setIdToken(ID_TOKEN);

        LoginResponse expectedResponse = LoginResponse.builder()
                .name(NAME)
                .refreshToken("refresh-token")
                .build();
        loginResult = LoginResult.from(
                "access-token",
                expectedResponse
        );
    }

    // 신규 Google 계정이면 사용자 레코드를 생성하고 토큰을 발급한다.
    @Test
    void signIn_registersNewUserWhenNotExists() {
        // Given
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.empty());
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);

        // When
        LoginResult result = googleOAuthService.signIn(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                 // 액세스 토큰이 반환된다.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");               // 리프레시 토큰이 함께 반환된다.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // 응답에 이름이 포함된다.

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);              // 저장된 사용자 정보를 검증한다.
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);                                  // 이메일이 프로필과 동일하다.
        assertThat(saved.getName()).isEqualTo(NAME);                                     // 이름이 프로필과 동일하다.
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);                // 공급자가 GOOGLE 로 저장된다.
        verify(loginService).issueTokens(saved);                                       // 신규 사용자로 토큰 발급이 이루어진다.
    }
  
    // 기존 계정이 있으면 그대로 사용하고 추가 저장은 하지 않는다.
    @Test
    void signIn_reusesExistingUser() {
        // Given
        User existingUser = User.createOAuthUser(EMAIL, "Old Name", null);
        setId(existingUser, UUID.randomUUID());
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.of(existingUser));
        when(loginService.issueTokens(existingUser)).thenReturn(loginResult);

        // When
        LoginResult result = googleOAuthService.signIn(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                 // 액세스 토큰이 전달된다.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");               // 리프레시 토큰이 전달된다.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // 응답 이름은 토큰 결과 기준이다.
        verify(userRepository, never()).save(Mockito.any());                            // 추가 저장이 발생하지 않는다.
        verify(loginService).issueTokens(existingUser);                                 // 기존 사용자로 토큰 발급이 호출된다.

    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
