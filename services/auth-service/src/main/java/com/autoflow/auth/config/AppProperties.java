package com.autoflow.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Frontend frontend = new Frontend();
    private Cookie cookie = new Cookie();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private int accessTokenExpiryMinutes = 15;
        private int refreshTokenExpiryDays = 7;
    }

    @Getter @Setter
    public static class Frontend {
        private String redirectUrl = "http://localhost:3000";
    }

    @Getter @Setter
    public static class Cookie {
        private boolean secure = false;
        private String sameSite = "Lax";
    }
}

