package com.example.springaichat.dto;

/**
 * 登录 / 刷新令牌响应 DTO
 *
 * <p>为保持向后兼容，老字段 {@code token} 仍然返回（值等于 accessToken）。
 * 新前端请直接使用 accessToken / refreshToken / expiresIn 三字段。
 */
public class LoginResponse {

    private String token;           // 兼容旧前端：= accessToken
    private String accessToken;     // 访问令牌，30min
    private String refreshToken;    // 刷新令牌，7d
    private Long expiresIn;         // accessToken 有效秒数，方便前端提前刷新

    private Long userId;
    private String username;

    public LoginResponse() {
    }

    /**
     * 兼容旧签名（单 Token 构造）
     */
    public LoginResponse(String token, Long userId, String username) {
        this.token = token;
        this.accessToken = token;
        this.userId = userId;
        this.username = username;
    }

    public LoginResponse(String accessToken, String refreshToken, Long expiresIn, Long userId, String username) {
        this.token = accessToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
        // 兼容：手动 setToken 时同步 accessToken
        if (this.accessToken == null) {
            this.accessToken = token;
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.token = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
