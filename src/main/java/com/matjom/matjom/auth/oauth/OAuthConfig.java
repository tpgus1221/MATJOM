package com.matjom.matjom.auth.oauth;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Google OAuth 검증에 사용하는 RestTemplate 및 관련 빈을 정의한다.
 */
@Configuration
public class OAuthConfig {

    @Bean
    public RestTemplate googleOAuthRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}