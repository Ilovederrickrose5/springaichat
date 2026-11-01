package com.example.springaichat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求 DTO
 */
public class RefreshTokenRequest {

    @NotBlank(message = "refresh Token 不能为空")
    private String refreshToken;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
