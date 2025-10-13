package com.matjom.matjom.common.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;    // 로그인 식별자
    private final String name;     // 사용자 이름

    public CustomUserDetails(UUID userId, String email, String name) {
        this.userId = userId;
        this.email = email;
        this.name = name;
    }

    // 권한: ROLE_USER 고정
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(() -> "ROLE_USER");
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return name;
    }

    // 계정 상태 관련 메서드 (모두 true로 설정)
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
