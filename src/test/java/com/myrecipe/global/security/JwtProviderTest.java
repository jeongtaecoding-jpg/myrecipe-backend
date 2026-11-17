package com.myrecipe.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myrecipe.global.exception.BusinessException;
import com.myrecipe.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

// 단위 테스트: 스프링·DB 없이 토큰 발급·검증 규칙만 확인한다. 테스트용 비밀 키는 테스트가 직접 넣는다(CODE_CONVENTION).
class JwtProviderTest {

    static final String SECRET = "test-secret-key-for-jwt-provider-0123456789";   // 32바이트 이상
    static final JwtConfig CONFIG = new JwtConfig(SECRET, 1800, 1209600);

    JwtProvider jwtProvider = new JwtProvider(CONFIG);

    @Test
    void 발급한_Access_Token을_검증하면_회원_id가_나온다() {
        // given
        String token = jwtProvider.createAccessToken(1L);

        // when & then
        assertThat(jwtProvider.parseAccessToken(token)).isEqualTo(1L);
    }

    @Test
    void 만료된_토큰이면_TOKEN_EXPIRED() {
        // given: 1시간 전에 발급된 30분짜리 토큰 (시계를 과거로 돌려서 발급)
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expired = new JwtProvider(CONFIG, oneHourAgo).createAccessToken(1L);

        // when & then
        assertErrorCode(() -> jwtProvider.parseAccessToken(expired), ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 다른_키로_서명된_토큰이면_TOKEN_INVALID() {
        // given: 키를 모르는 사람이 만든 토큰 (위조)
        JwtConfig otherConfig = new JwtConfig("another-secret-key-not-ours-0123456789!!", 1800, 1209600);
        String forged = new JwtProvider(otherConfig).createAccessToken(1L);

        // when & then
        assertErrorCode(() -> jwtProvider.parseAccessToken(forged), ErrorCode.TOKEN_INVALID);
    }

    @Test
    void Refresh_Token을_Access_Token_자리에_쓰면_TOKEN_INVALID() {
        // given: 같은 키로 서명했지만 type이 refresh인 토큰 (QA9)
        String refreshToken = Jwts.builder()
                .subject("1")
                .claim(JwtProvider.TYPE_CLAIM, "refresh")
                .issuer(JwtProvider.ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        // when & then
        assertErrorCode(() -> jwtProvider.parseAccessToken(refreshToken), ErrorCode.TOKEN_INVALID);
    }

    @Test
    void HS256이_아닌_알고리즘으로_서명된_토큰이면_TOKEN_INVALID() {
        // given: 키가 64바이트 이상이면 HS512 서명도 수학적으로는 검증된다. 그래도 HS256만 받아야 한다
        String longSecret = "x".repeat(64);
        JwtProvider provider = new JwtProvider(new JwtConfig(longSecret, 1800, 1209600));
        String hs512Token = Jwts.builder()
                .subject("1")
                .claim(JwtProvider.TYPE_CLAIM, JwtProvider.ACCESS_TYPE)
                .issuer(JwtProvider.ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();

        // when & then
        assertErrorCode(() -> provider.parseAccessToken(hs512Token), ErrorCode.TOKEN_INVALID);
    }

    @Test
    void 형식이_깨진_문자열이면_TOKEN_INVALID() {
        assertErrorCode(() -> jwtProvider.parseAccessToken("this.is.not-a-jwt"), ErrorCode.TOKEN_INVALID);
        assertErrorCode(() -> jwtProvider.parseAccessToken(""), ErrorCode.TOKEN_INVALID);
    }

    private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
