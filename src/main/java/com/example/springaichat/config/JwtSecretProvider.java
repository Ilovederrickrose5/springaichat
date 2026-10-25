package com.example.springaichat.config;

import com.example.springaichat.util.JwtSecretValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 在 Bean 初始化阶段解析 JWT 签名密钥：
 * 未配置或长度不足时，dev 生成本次进程内随机密钥并告警，prod 直接启动失败。
 */
@Component
public class JwtSecretProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtSecretProvider.class);

    private final String secret;

    public JwtSecretProvider(@Value("${jwt.secret:}") String configuredSecret, Environment environment) {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean configured = configuredSecret != null && !configuredSecret.isBlank()
                && configuredSecret.length() >= JwtSecretValidator.MIN_SECRET_LENGTH;

        this.secret = JwtSecretValidator.resolve(configuredSecret, prodProfile, JwtSecretProvider::generateRandomSecret);

        if (!configured) {
            logger.warn("JWT_SECRET 未配置或长度不足 {} 字符，已生成本次进程内随机密钥：重启后所有旧 Token 失效；生产环境必须配置 JWT_SECRET",
                    JwtSecretValidator.MIN_SECRET_LENGTH);
        }
    }

    public String getSecret() {
        return secret;
    }

    private static String generateRandomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
