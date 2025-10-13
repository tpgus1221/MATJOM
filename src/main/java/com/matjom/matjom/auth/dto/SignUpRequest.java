package com.matjom.matjom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignUpRequest {

    @Schema(description = "사용자 이메일", example = "user@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "사용자 이름", example = "홍길동")
    @NotBlank
    private String name;

    @Schema(description = "비밀번호(8~32자)", example = "Password123!")
    @NotBlank
    @Size(min = 8, max = 32, message = "비밀번호는 8~32자로 입력해야 합니다.")
    private String password;
}