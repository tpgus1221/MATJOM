package com.matjom.matjom.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResult {

    private String accessToken;
    private LoginResponse response;

    public static LoginResult from(String accessToken, LoginResponse response) {
        return LoginResult.builder()
                .accessToken(accessToken)
                .response(response)
                .build();
    }
}
