package com.matjom.matjom.user.controller;

import com.matjom.matjom.user.dto.ChangePasswordRequest;
import com.matjom.matjom.user.dto.WithdrawRequest;
import com.matjom.matjom.user.service.ChangePasswordService;
import com.matjom.matjom.user.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 계정 API")
public class UserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final WithdrawService withdrawService;
    private final ChangePasswordService changePasswordService;

    @PatchMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 삭제한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public void withdraw(@RequestHeader(HttpHeaders.AUTHORIZATION) String header,
                         @Valid @RequestBody WithdrawRequest request) {
        String accessToken = header.replaceFirst(BEARER_PREFIX, "");
        withdrawService.withdraw(accessToken, request);
    }

    @PatchMapping("/password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 검증하고 새 비밀번호로 업데이트한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public void changePassword(@RequestHeader(HttpHeaders.AUTHORIZATION) String header,
                               @Valid @RequestBody ChangePasswordRequest request) {
        String accessToken = header.replaceFirst(BEARER_PREFIX, "");
        changePasswordService.changePassword(accessToken, request);
    }
}
