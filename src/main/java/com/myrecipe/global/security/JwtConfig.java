package com.myrecipe.global.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yaml의 jwt.* 값을 묶는다. 값이 잘못되면 앱이 시작되지 않게 여기서 바로 막는다(INTEGRATION "JWT 필터 동작").
// HS256 서명 키는 32바이트(256비트) 이상이어야 안전하다. 짧은 키로 조용히 동작하는 것보다 시작 실패가 낫다.
@ConfigurationProperties(prefix = "jwt")
public record JwtConfig(String secret, long accessExpirationSeconds, long refreshExpirationSeconds) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtConfig {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (accessExpirationSeconds <= 0 || refreshExpirationSeconds <= 0) {
            throw new IllegalStateException("JWT 유효 시간은 0보다 커야 합니다.");
        }
    }
}
