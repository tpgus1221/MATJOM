package com.matjom.matjom.auth.controller;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.dto.ReissueRequest;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.auth.service.GoogleOAuthService;
import com.matjom.matjom.auth.service.LoginService;
import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.auth.service.ReissueService;
import com.matjom.matjom.auth.service.SignUpService;
import com.matjom.matjom.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SignUpService signUpService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final ReissueService reissueService;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/signup")
    @Operation(summary = "로컬 회원가입", description = "이메일로 신규 가입하고 로그인 토큰을 발급한다.")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignUpRequest request) {
        LoginResult result = signUpService.signUp(request);
        return respondWithTokens(result);
    }

    @PostMapping("/login")
    @Operation(summary = "로컬 로그인", description = "이메일과 비밀번호로 로그인하고 토큰을 발급한다.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);
        return respondWithTokens(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 액세스 토큰을 블랙리스트에 등록하고 리프레시 토큰을 삭제한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String header) {
        String accessToken = header.replaceFirst(BEARER_PREFIX, "");
        logoutService.logout(accessToken);
    }

    @PostMapping("/oauth/google/callback")
    @Operation(summary = "Google OAuth 로그인", description = "Google ID 토큰을 검증하고 토큰을 발급한다.")
    public ResponseEntity<ApiResponse<LoginResponse>> googleCallback(@Valid @RequestBody GoogleOAuthRequest request) {
        LoginResult result = googleOAuthService.signIn(request);
        return respondWithTokens(result);
    }

    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "만료된 액세스 토큰을 재발급한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String header,
            @RequestBody ReissueRequest request
    ) {
        String accessToken = header.replaceFirst(BEARER_PREFIX, "");
        String refreshToken = request.getRefreshToken();
        LoginResult result = reissueService.reissue(accessToken, refreshToken);
        return respondWithTokens(result);
    }

    private ResponseEntity<ApiResponse<LoginResponse>> respondWithTokens(LoginResult result) {

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + result.getAccessToken())
                .body(ApiResponse.ok(result.getResponse()));
    }
}