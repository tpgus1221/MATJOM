package com.matjom.matjom.auth.oauth;

import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Google ID 토큰을 검증하고 인증에 필요한 프로필 정보를 구축하는 클라이언트.
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private final RestTemplate googleOAuthRestTemplate;
    private final GoogleOAuthProperties properties;

    /**
     * Google ID 토큰을 검증하고 유효한 사용자 프로필을 반환한다.
     *
     * @param idToken 프론트엔드에서 전달받은 Google ID 토큰
     * @return 검증에 성공한 사용자의 프로필 정보
     */
    public GoogleOAuthProfile verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new AuthException(ErrorCode.INVALID_TOKEN, "Google ID token is missing");
        }

        if (!properties.hasClientIds()) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS, "Google client id is not configured");
        }

        try {
            ResponseEntity<GoogleTokenInfoResponse> response = googleOAuthRestTemplate.getForEntity(
                    properties.getTokenInfoEndpoint() + "?id_token=" + idToken,
                    GoogleTokenInfoResponse.class
            );

            GoogleTokenInfoResponse body = response.getBody();
            if (body == null) {
                throw new AuthException(ErrorCode.INVALID_TOKEN, "Failed to parse Google token info");
            }

            if (!properties.getAllowedClientIds().contains(body.getAud())) {
                throw new AuthException(ErrorCode.INVALID_TOKEN, "Google token audience mismatch");
            }

            if (!body.isEmailVerified()) {
                throw new AuthException(ErrorCode.INVALID_TOKEN, "Google email is not verified");
            }

            if (!StringUtils.hasText(body.getEmail())) {
                throw new AuthException(ErrorCode.INVALID_TOKEN, "Google token missing email");
            }

            return GoogleOAuthProfile.builder()
                    .email(body.getEmail())
                    .name(StringUtils.hasText(body.getName()) ? body.getName() : body.getEmail())
                    .subject(body.getSub())
                    .picture(body.getPicture())
                    .build();
        } catch (RestClientException ex) {
            log.warn("Failed to verify Google ID token", ex);
            throw new AuthException(ErrorCode.INVALID_TOKEN, "Google token verification failed");
        }
    }
}