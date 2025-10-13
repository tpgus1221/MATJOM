package com.matjom.matjom.auth.oauth;

import lombok.Builder;
import lombok.Getter;

/**
 * Google OAuth 인증으로부터 획득한 핵심 사용자 프로필 값을 담는다.
 */
@Getter
@Builder
public class GoogleOAuthProfile {

    private final String email;
    private final String name;
    private final String subject;
    private final String picture;
}