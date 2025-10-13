package com.matjom.matjom.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Google tokeninfo 엔드포인트 응답을 역직렬화하기 위한 DTO.
 */
@Getter
@NoArgsConstructor
public class GoogleTokenInfoResponse {

    private String aud;
    private String sub;
    private String email;

    @JsonProperty("email_verified")
    private boolean emailVerified;

    private String name;
    private String picture;

    public boolean isEmailVerified() {
        return emailVerified;
    }
}