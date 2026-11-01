package com.example.springaichat.util;

import com.example.springaichat.config.JwtSecretProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试：覆盖一次解析拿全声明（过滤器依赖）与类型隔离。
 */
class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        jwtUtil = new JwtUtil(new JwtSecretProvider(SECRET, environment));
        ReflectionTestUtils.setField(jwtUtil, "accessExpiration", 1800000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);
    }

    @Test
    void accessTokenContainsSubjectTypeAndJtiInOneParse() {
        Claims claims = jwtUtil.parseClaims(jwtUtil.generateAccessToken("alice"));

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("type", String.class)).isEqualTo(JwtUtil.TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void refreshTokenIsRejectedWhenValidatingAsAccess() {
        String refreshToken = jwtUtil.generateRefreshToken("alice");

        assertThat(jwtUtil.isValidWithoutUsername(refreshToken, JwtUtil.TYPE_ACCESS)).isFalse();
        assertThat(jwtUtil.isValidWithoutUsername(refreshToken, JwtUtil.TYPE_REFRESH)).isTrue();
    }

    @Test
    void tamperedTokenFailsSignatureValidation() {
        String token = jwtUtil.generateAccessToken("alice");
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwtUtil.parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }
}
