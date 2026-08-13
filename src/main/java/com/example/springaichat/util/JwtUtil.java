package com.example.springaichat.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT 工具类 —— 双 Token 方案（access / refresh）
 *
 * <p>Token 内部声明：
 * <ul>
 *   <li>sub — username（主体）</li>
 *   <li>iat — 签发时间</li>
 *   <li>exp — 过期时间</li>
 *   <li>jti — UUID 指纹，用于 Redis 黑名单/比对</li>
 *   <li>type — access / refresh，防止 refresh Token 被当 access 用</li>
 * </ul>
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_JTI = "jti";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration:1800000}")
    private Long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    // ==================== 提取声明 ====================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_JTI, String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ==================== 生成 Token ====================

    /**
     * 生成 access Token（30 分钟）
     */
    public String generateAccessToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
        claims.put(CLAIM_JTI, UUID.randomUUID().toString().replace("-", ""));
        return createToken(claims, username, accessExpiration);
    }

    /**
     * 生成 refresh Token（7 天）
     */
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
        claims.put(CLAIM_JTI, UUID.randomUUID().toString().replace("-", ""));
        return createToken(claims, username, refreshExpiration);
    }

    /**
     * 旧方法保留：兼容现有代码调用，等价于生成 access Token
     */
    public String generateToken(String username) {
        return generateAccessToken(username);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    // ==================== 校验 Token ====================

    /**
     * 基础校验：签名、过期、subject 匹配
     */
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * 带类型的校验：只允许指定类型通过
     */
    public Boolean validateToken(String token, String username, String expectedType) {
        if (!validateToken(token, username)) {
            return false;
        }
        return expectedType.equals(extractTokenType(token));
    }

    /**
     * 仅解析 + 基础校验，不校验 subject（用在 refresh 接口时，解析完再从 Redis 查）
     */
    public Boolean isValidWithoutUsername(String token, String expectedType) {
        try {
            if (isTokenExpired(token)) {
                return false;
            }
            return expectedType.equals(extractTokenType(token));
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 辅助 ====================

    public Long getAccessExpiration() {
        return accessExpiration;
    }

    public Long getRefreshExpiration() {
        return refreshExpiration;
    }
}
