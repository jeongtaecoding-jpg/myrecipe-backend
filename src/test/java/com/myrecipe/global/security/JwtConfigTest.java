package com.myrecipe.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    @Test
    void 비밀_키가_32바이트보다_짧으면_만들_수_없다() {
        assertThatThrownBy(() -> new JwtConfig("short-secret", 1800, 1209600))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 비밀_키가_없으면_만들_수_없다() {
        // .env에 JWT_SECRET이 없을 때: 조용히 동작하지 않고 시작 시점에 실패해야 한다
        assertThatThrownBy(() -> new JwtConfig(null, 1800, 1209600))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 비밀_키가_32바이트_이상이면_만들어진다() {
        assertThatCode(() -> new JwtConfig("a".repeat(32), 1800, 1209600))
                .doesNotThrowAnyException();
    }
}
