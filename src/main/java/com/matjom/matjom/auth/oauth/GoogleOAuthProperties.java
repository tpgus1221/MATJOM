package com.matjom.matjom.auth.oauth;

import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Google OAuth 검증에 필요한 엔드포인트와 허용 client id 목록을 보관한다.
 */
@Getter
@Component
@ConfigurationProperties(prefix = "oauth.google")
public class GoogleOAuthProperties {

    private String tokenInfoEndpoint;
    private List<String> allowedClientIds;

    public void setTokenInfoEndpoint(String tokenInfoEndpoint) {
        this.tokenInfoEndpoint = tokenInfoEndpoint;
    }

    public void setAllowedClientIds(List<String> allowedClientIds) {
        this.allowedClientIds = allowedClientIds;
    }

    public boolean hasClientIds() {
        return !CollectionUtils.isEmpty(allowedClientIds);
    }
}