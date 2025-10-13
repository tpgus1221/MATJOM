package com.matjom.matjom.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    @Schema(description = "현재 비밀번호", example = "currentPass123")
    @NotBlank
    private String currentPassword;

    @Schema(description = "새 비밀번호(8~32자)", example = "newPass456!")
    @NotBlank
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters.")
    private String newPassword;
}