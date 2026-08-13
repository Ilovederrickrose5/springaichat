package com.example.springaichat.service;

import com.example.springaichat.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 存储服务
 *
 * <p>职责：
 * <ul>
 *   <li>Redis 存储 refresh Token（按 username 绑定，同一用户后登录踢前登录）</li>
 *   <li>access Token 登出后写入黑名单（TTL = 该 Token 剩余有效期）</li>
 *   <li>refresh Token 不支持「多次复用」：每次 refresh 发新 Token，旧 refresh 失效</li>
 * </ul>
 */
@Service
public class TokenStoreService {

    private static final Logger logger = LoggerFactory.getLogger(TokenStoreService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtil jwtUtil;

    @Value("${jwt.redis.refresh-prefix:jwt:refresh:}")
    private String refreshPrefix;

    @Value("${jwt.redis.blacklist-prefix:jwt:blacklist:}")
    private String blacklistPrefix;

    public TokenStoreService(StringRedisTemplate stringRedisTemplate, JwtUtil jwtUtil) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtil = jwtUtil;
    }

    // ==================== refresh Token ====================

    /**
     * 保存 refresh Token 到 Redis（TTL = refresh 总有效期）
     * 同一 username 会覆盖旧值，自然实现「新登录踢掉旧 Token」
     */
    public void saveRefreshToken(String username, String refreshToken) {
        String key = refreshPrefix + username;
        long ttlMillis = jwtUtil.getRefreshExpiration();
        try {
            stringRedisTemplate.opsForValue().set(key, refreshToken, ttlMillis, TimeUnit.MILLISECONDS);
            logger.info("Saved refresh token for user={}, key={}", username, key);
        } catch (Exception e) {
            logger.error("Failed to save refresh token for user={}", username, e);
            throw new RuntimeException("保存 refresh Token 失败", e);
        }
    }

    /**
     * 校验 refresh Token：必须等于 Redis 中该 username 当前绑定的值，且未过期
     */
    public boolean isValidRefreshToken(String username, String refreshToken) {
        String key = refreshPrefix + username;
        try {
            String stored = stringRedisTemplate.opsForValue().get(key);
            if (stored == null) {
                logger.warn("Refresh token not found in Redis for user={}", username);
                return false;
            }
            return stored.equals(refreshToken);
        } catch (Exception e) {
            logger.error("Failed to validate refresh token for user={}", username, e);
            return false;
        }
    }

    /**
     * 删除该 username 绑定的 refresh Token（登出 / 强制下线）
     */
    public void deleteRefreshToken(String username) {
        String key = refreshPrefix + username;
        try {
            stringRedisTemplate.delete(key);
            logger.info("Deleted refresh token for user={}", username);
        } catch (Exception e) {
            logger.error("Failed to delete refresh token for user={}", username, e);
        }
    }

    // ==================== access Token 黑名单 ====================

    /**
     * 将 access Token 加入黑名单（登出场景）
     * TTL = 该 Token 剩余有效期，避免无限堆积
     */
    public void blacklistAccessToken(String accessToken) {
        Date expiration;
        try {
            expiration = jwtUtil.extractExpiration(accessToken);
        } catch (Exception e) {
            logger.warn("Cannot parse access token expiration, skip blacklist");
            return;
        }
        long ttlMillis = expiration.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        String key = blacklistPrefix + jwtUtil.extractTokenId(accessToken);
        try {
            stringRedisTemplate.opsForValue().set(key, "1", ttlMillis, TimeUnit.MILLISECONDS);
            logger.info("Blacklisted access token jti key={}", key);
        } catch (Exception e) {
            logger.error("Failed to blacklist access token", e);
        }
    }

    /**
     * 判断 access Token 是否已被加入黑名单
     */
    public boolean isAccessTokenBlacklisted(String accessToken) {
        String jti;
        try {
            jti = jwtUtil.extractTokenId(accessToken);
        } catch (Exception e) {
            return false;
        }
        if (jti == null) {
            return false;
        }
        String key = blacklistPrefix + jti;
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Failed to check blacklist, key={}", key, e);
            // Redis 挂掉时保守放行（总比全站登录不了好），也可改为拒绝，按业务权衡
            return false;
        }
    }
}
