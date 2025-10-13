package com.matjom.matjom.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WithdrawRequest {

    @NotBlank
    @Size(min = 8, max = 32, message = "비밀번호는 8~32자로 입력해야 합니다.")
    private String password;
}
