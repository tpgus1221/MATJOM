package com.matjom.matjom.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.dto.ChangePasswordRequest;
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
class ChangePasswordServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                        // ???뮞?紐꾩뒠 ??り쉭???醫뤾쿃.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440010");
    private static final String EMAIL = "user@example.com";                           // ???뮞?紐꾩뒠 ??李??
    private static final String NAME = "User";                                        // ???뮞?紐꾩뒠 ??已?
    private static final String CURRENT_PASSWORD = "currentPass123";                  // ?袁⑹삺 ??쑬?甕곕뜇??
    private static final String ENCODED_CURRENT = "encoded-current";                  // ?酉??遺얜쭆 ?袁⑹삺 ??쑬?甕곕뜇??
    private static final String NEW_PASSWORD = "newPass456";                          // ????쑬?甕곕뜇??
    private static final String ENCODED_NEW = "encoded-new";                          // ?酉??遺얜쭆 ????쑬?甕곕뜇??

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                         // JWT ?醫뤾쿃 ?⑤벀???筌뤴뫂沅?
    @Mock
    private UserRepository userRepository;                                             // ????????關??筌뤴뫂沅?
    @Mock
    private PasswordEncoder passwordEncoder;                                           // ??쑬?甕곕뜇???紐꾪맜??筌뤴뫂沅?
    @Mock
    private LogoutService logoutService;                                               // 嚥≪뮄??袁⑹뜍 ??뺥돩??筌뤴뫂沅?

    @InjectMocks
    private ChangePasswordService changePasswordService;                               // ???뮞????????뺥돩??

    private User user;                                                                 // 嚥≪뮇類???????酉???

    @BeforeEach
    void setUp() {
        user = User.createLocalUser(EMAIL, NAME, ENCODED_CURRENT);                     // Helper: ???뮞?紐꾩뒠 嚥≪뮇類?????癒? ??밴쉐??뺣뼄.
        setField(user, "id", USER_ID);
    }

    // ??쑬?甕곕뜇??野꺜筌앹빘肉??源껊궗??롢늺 ????쑬?甕곕뜇?뉑에?癰궰野껋?釉??嚥≪뮄??袁⑹뜍??쀪텚??
    @Test
    void changePassword_updatesPasswordAndLogsOut() {
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW);

        changePasswordService.changePassword(ACCESS_TOKEN, request);

        assertThat(user.getPassword()).isEqualTo(ENCODED_NEW);                         // ???????쑬?甕곕뜇?뉐첎? ??揶쏅??앮에?癰궰野껋럥留??
        verify(userRepository).save(user);                                             // 癰궰野???鍮?????貫留??
        verify(logoutService).logoutHelper(USER_ID, ACCESS_TOKEN);                     // 疫꿸퀣???醫뤾쿃???얜똾??遺얜쭆??
    }

    // ?袁⑹삺 ??쑬?甕곕뜇?뉐첎? ???귐됥늺 ??됱뇚??????????館釉?쭪? ??낅뮉??
    @Test
    void changePassword_throwsWhenCurrentPasswordMismatch() {
        ChangePasswordRequest request = buildRequest("wrong", NEW_PASSWORD);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", ENCODED_CURRENT)).thenReturn(false);

        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(userRepository, never()).save(user);                                    // ??쑬?甕곕뜇??癰궰野껋럩?????貫由븝쭪? ??낅뮉??
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);            // 嚥≪뮄??袁⑹뜍???紐꾪뀱??? ??낅뮉??
    }

    // ????癒? 筌≪뼚? 筌륁궢釉?쭖??紐꾩쵄 ??쎈솭 ??됱뇚????륁춭??
    @Test
    void changePassword_throwsWhenUserNotFound() {
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(passwordEncoder, never()).matches(CURRENT_PASSWORD, ENCODED_CURRENT);   // ??쑬?甕곕뜇??野꺜筌앹빘? ??뺣즲??? ??낅뮉??
    }

    // OAuth ?④쑴??? ??쑬?甕곕뜇??癰궰野껋럩????됱뒠??? ??낅뮉??
    @Test
    void changePassword_throwsWhenUserIsNotLocal() {
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        User oauthUser = User.createOAuthUser(EMAIL, NAME, null);
        setField(oauthUser, "id", USER_ID);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH_PASSWORD_CHANGE_NOT_ALLOWED);

        verify(passwordEncoder, never()).matches(CURRENT_PASSWORD, ENCODED_CURRENT);   // ??쑬?甕곕뜇??野꺜筌앹빘? ??묐뻬??? ??낅뮉??
    }

    private ChangePasswordRequest buildRequest(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();                   // Helper: ??쑬?甕곕뜇??癰궰野??遺욧퍕???닌딄쉐??뺣뼄.
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
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
