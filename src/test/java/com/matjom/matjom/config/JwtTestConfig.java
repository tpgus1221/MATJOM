package com.matjom.matjom.config;

import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;

@TestConfiguration
public class JwtTestConfig {

    @Bean
    @Primary
    public JwtTokenProvider jwtTokenProvider() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ReflectionTestUtils.invokeMethod(provider, "init");
        return provider;
    }
}
