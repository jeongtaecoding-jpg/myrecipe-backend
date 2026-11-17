package com.myrecipe.global.security;

import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Access Token을 만들고 검증한다. (Refresh Token 발급·검증은 Refresh Token 단계에서 추가)
// 토큰 내용: sub=회원 id, type=access, iss=myrecipe, iat=발급 시각, exp=만료 시각
@Component
public class JwtProvider {

    static final String ISSUER = "myrecipe";
    static final String TYPE_CLAIM = "type";
    static final String ACCESS_TYPE = "access";
    private static final String ALGORITHM = "HS256";

    private final SecretKey key;
    private final long accessExpirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtProvider(JwtConfig config) {
        this(config, Clock.systemUTC());
    }

    // 테스트에서 "과거에 발급된 토큰"을 만들기 위해 시계를 바꿔 끼울 수 있게 한다
    JwtProvider(JwtConfig config, Clock clock) {
        this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpirationSeconds = config.accessExpirationSeconds();
        this.clock = clock;
    }

    public String createAccessToken(Long memberId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpirationSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // 성공하면 회원 id. 만료면 TOKEN_EXPIRED, 그 밖의 모든 문제(위조·형식 오류·다른 발급자·Refresh Token)는 TOKEN_INVALID.
    public Long parseAccessToken(String token) {
        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token);
            // 토큰 헤더의 alg를 그대로 믿지 않고 HS256만 받는다
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
            claims = jws.getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 같은 키로 서명한 Refresh Token(14일)을 Access Token 자리에 쓰지 못하게 종류를 확인한다(QA9)
        if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
