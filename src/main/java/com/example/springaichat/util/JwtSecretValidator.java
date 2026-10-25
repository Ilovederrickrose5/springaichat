package com.example.springaichat.util;

import java.util.function.Supplier;

/**
 * JWT 密钥校验：密钥为空或长度不足 32 字符时，dev 环境降级为进程内随机密钥，
 * prod 环境直接拒绝启动，避免使用可预测的默认密钥。
 */
public final class JwtSecretValidator {

    public static final int MIN_SECRET_LENGTH = 32;

    private JwtSecretValidator() {
    }

    /**
     * @param secret              配置的 jwt.secret（可能为空）
     * @param prodProfile         是否激活 prod profile
     * @param randomSecretSupplier dev 环境下的随机密钥生成器
     * @return 可用的签名密钥
     * @throws IllegalStateException prod 环境密钥缺失或过短
     */
    public static String resolve(String secret, boolean prodProfile, Supplier<String> randomSecretSupplier) {
        if (secret != null && !secret.isBlank() && secret.length() >= MIN_SECRET_LENGTH) {
            return secret;
        }
        if (prodProfile) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置或长度不足 " + MIN_SECRET_LENGTH + " 字符，prod 环境拒绝启动");
        }
        return randomSecretSupplier.get();
    }
}
