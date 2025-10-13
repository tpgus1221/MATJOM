package com.matjom.matjom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoogleOAuthRequest {

    @Schema(description = "Google ID 토큰", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6I...")
    @NotBlank
    private String idToken;
}