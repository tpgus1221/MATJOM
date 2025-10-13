package com.matjom.matjom.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                        // 테스트용 액세스 토큰.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001");
    private static final String EMAIL = "user@example.com";                           // 테스트용 이메일.
    private static final String NAME = "User";                                        // 테스트용 이름.
    private static final String PASSWORD = "password123";                             // 입력 비밀번호.
    private static final String ENCODED_PASSWORD = "encoded-password";                // 암호화된 비밀번호.

    @Mock
    private UserRepository userRepository;                                            // 사용자 저장소 모킹.
    @Mock
    private DeletedUserRepository deletedUserRepository;                              // 탈퇴 사용자 저장소 모킹.
    @Mock
    private JwtTokenProvider jwtTokenProvider;                                        // 토큰 공급자 모킹.
    @Mock
    private LogoutService logoutService;                                              // 로그아웃 서비스 모킹.
    @Mock
    private PasswordEncoder passwordEncoder;                                          // 비밀번호 인코더 모킹.

    @InjectMocks
    private WithdrawService withdrawService;                                          // 테스트 대상 서비스.

    private User localUser;                                                           // 로컬 사용자 엔티티.
    private User oauthUser;                                                           // OAuth 사용자 엔티티.

    @BeforeEach
    void setUp() {
        localUser = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);              // Helper: 로컬 계정을 생성한다.
        setId(localUser, USER_ID);

        oauthUser = User.createOAuthUser(EMAIL, NAME, null);                          // Helper: OAuth 계정을 생성한다.
        setId(oauthUser, USER_ID);
    }

    // 로컬 사용자가 올바른 비밀번호로 탈퇴하면 기록이 이동하고 로그아웃된다.
    @Test
    void withdraw_localUser_withValidPassword() {
        WithdrawRequest request = new WithdrawRequest();
        request.setPassword(PASSWORD);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(localUser));
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        withdrawService.withdraw(ACCESS_TOKEN, request);

        ArgumentCaptor<DeletedUser> captor = ArgumentCaptor.forClass(DeletedUser.class); // 탈퇴 사용자 저장을 검증한다.
        verify(deletedUserRepository).save(captor.capture());
        DeletedUser saved = captor.getValue();
        Assertions.assertThat(readField(saved, "id")).isEqualTo(USER_ID);            // 삭제 테이블에도 동일한 ID가 보관된다.
        Assertions.assertThat(readField(saved, "email")).isEqualTo(EMAIL);           // 이메일이 보관된다.
        Assertions.assertThat(readField(saved, "provider")).isEqualTo(AuthProvider.LOCAL); // 공급자가 기록된다.

        verify(userRepository).delete(localUser);                                     // 원본 사용자 레코드가 삭제된다.
        verify(logoutService).logoutHelper(USER_ID, ACCESS_TOKEN);                    // 토큰이 무효화된다.
    }

    // 비밀번호가 틀리면 예외를 던지고 아무 작업도 하지 않는다.
    @Test
    void withdraw_localUser_withInvalidPassword() {
        WithdrawRequest request = new WithdrawRequest();
        request.setPassword("wrong");
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(localUser));
        when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(deletedUserRepository, never()).save(org.mockito.Mockito.any());       // 탈퇴 기록이 생성되지 않는다.
        verify(userRepository, never()).delete(localUser);                            // 사용자 삭제도 일어나지 않는다.
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);           // 로그아웃 처리가 호출되지 않는다.
    }

    // OAuth 사용자는 비밀번호 없이도 탈퇴가 가능하다.
    @Test
    void withdraw_oauthUser_withoutPassword() {
        WithdrawRequest request = new WithdrawRequest();                              // 비밀번호가 비어 있어도 허용된다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(oauthUser));

        withdrawService.withdraw(ACCESS_TOKEN, request);

        verify(passwordEncoder, never()).matches(org.mockito.Mockito.any(), org.mockito.Mockito.any()); // 비밀번호 검증이 없다.
        verify(deletedUserRepository).save(org.mockito.Mockito.any(DeletedUser.class));                 // 탈퇴 기록이 남는다.
        verify(userRepository).delete(oauthUser);                                                       // 사용자 레코드가 삭제된다.
        verify(logoutService).logoutHelper(USER_ID, ACCESS_TOKEN);                                      // 토큰이 무효화된다.
    }

    // 사용자를 찾지 못하면 인증 실패 예외를 던진다.
    @Test
    void withdraw_userNotFound() {
        WithdrawRequest request = new WithdrawRequest();
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(deletedUserRepository, never()).save(org.mockito.Mockito.any());       // 탈퇴 기록이 생성되지 않는다.
        verify(userRepository, never()).delete(org.mockito.Mockito.any());            // 사용자 삭제가 일어나지 않는다.
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);           // 로그아웃도 호출되지 않는다.
    }

    private void setId(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
