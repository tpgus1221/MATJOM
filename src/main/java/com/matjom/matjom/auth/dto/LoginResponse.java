package com.matjom.matjom.auth.dto;

import com.matjom.matjom.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String name;
    private String refreshToken;

    public static LoginResponse from(User user, String refreshToken) {
        return LoginResponse.builder()
                .name(user.getName())
                .refreshToken(refreshToken)
                .build();
    }
}
