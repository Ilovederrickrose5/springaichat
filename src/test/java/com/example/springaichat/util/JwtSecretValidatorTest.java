package com.example.springaichat.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretValidatorTest {

    @Test
    void emptySecret_prodProfile_failsFast() {
        assertThatThrownBy(() -> JwtSecretValidator.resolve("", true, () -> "random"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shortSecret_prodProfile_failsFast() {
        assertThatThrownBy(() -> JwtSecretValidator.resolve("x".repeat(31), true, () -> "random"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validSecret_returnedAsIs() {
        String secret = "s".repeat(JwtSecretValidator.MIN_SECRET_LENGTH);

        assertThat(JwtSecretValidator.resolve(secret, true, () -> "random")).isEqualTo(secret);
        assertThat(JwtSecretValidator.resolve(secret, false, () -> "random")).isEqualTo(secret);
    }

    @Test
    void emptySecret_devProfile_generatesRandomSecret() {
        assertThat(JwtSecretValidator.resolve("", false, () -> "random-secret"))
                .isEqualTo("random-secret");
    }
}
