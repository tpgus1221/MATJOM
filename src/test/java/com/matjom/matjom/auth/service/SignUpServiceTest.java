package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    private static final String EMAIL = "user@example.com";                           // 테스트용 이메일.
    private static final String NAME = "User";                                        // 테스트용 이름.
    private static final String PASSWORD = "password123";                             // 입력 비밀번호.
    private static final String ENCODED_PASSWORD = "encoded-password";                // 암호화된 비밀번호.

    @Mock
    private UserRepository userRepository;                                             // 사용자 저장소 모킹.

    @Mock
    private PasswordEncoder passwordEncoder;                                           // 비밀번호 인코더 모킹.

    @Mock
    private LoginService loginService;                                                 // 로그인 서비스 모킹.

    @InjectMocks
    private SignUpService signUpService;                                               // 테스트 대상 서비스.

    private SignUpRequest request;                                                     // 회원가입 요청 DTO.

    @BeforeEach
    void setUp() {
        request = new SignUpRequest();                                                 // 테스트에 사용할 요청을 생성한다.
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        request.setName(NAME);
    }

    // 신규 회원이면 계정을 생성하고 토큰을 발급한다.
    @Test
    void signUp_createsLocalUser_andIssuesTokens() {
        // Given
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        LoginResponse expectedResponse = LoginResponse.builder()
                .name(NAME)
                .refreshToken("refresh-token")
                .build();
        LoginResult expected = LoginResult.from(
                "access-token",
                expectedResponse
        );
        when(loginService.issueTokens(any(User.class))).thenReturn(expected);

        // When
        LoginResult result = signUpService.signUp(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                  // 액세스 토큰이 그대로 반환된다.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");  // 리프레시 토큰이 함께 반환된다.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // 응답에 가입자가 표시된다.

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);              // 저장된 사용자 정보를 검증한다.
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);                                  // 저장된 이메일이 일치한다.
        assertThat(saved.getName()).isEqualTo(NAME);                                    // 저장된 이름이 일치한다.
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);                    // 비밀번호가 암호화된 값으로 저장된다.
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);                  // 공급자가 LOCAL 로 지정된다.

        verify(passwordEncoder).encode(PASSWORD);                                      // 비밀번호가 인코딩된다.
        verify(loginService).issueTokens(saved);                                       // 토큰 발급이 호출된다.
    }

    // 동일한 이메일이 이미 존재하면 예외를 던진다.
    @Test
    void signUp_throwsWhenEmailAlreadyExists() {
        // Given
        User existing = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(java.util.Optional.of(existing));

        // When & Then
        assertThrows(AuthException.class, () -> signUpService.signUp(request));         // 예외가 발생해야 한다.

        // Then
        verify(userRepository, never()).save(any());                                    // 사용자 저장은 일어나지 않는다.
        verify(loginService, never()).issueTokens(any());                               // 토큰 발급도 호출되지 않는다.
    }
}
