package com.example.springaichat.controller;

import com.example.springaichat.dto.LoginRequest;
import com.example.springaichat.dto.LoginResponse;
import com.example.springaichat.dto.RefreshTokenRequest;
import com.example.springaichat.dto.RegisterRequest;
import com.example.springaichat.entity.User;
import com.example.springaichat.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器（双 Token 方案）
 *
 * <ul>
 *   <li>POST /api/auth/register  注册</li>
 *   <li>POST /api/auth/login     登录 → 返回 access + refresh + expiresIn</li>
 *   <li>POST /api/auth/refresh   用 refresh 换新的 access + 新 refresh（rotation）</li>
 *   <li>POST /api/auth/logout    登出（需登录态，带 access Token header）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("userId", user.getId());
            response.put("username", user.getUsername());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 用户登录 — 返回双 Token
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse loginResponse = authService.login(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "登录成功");
            // 兼容字段：值 = accessToken
            response.put("token", loginResponse.getAccessToken());
            response.put("accessToken", loginResponse.getAccessToken());
            response.put("refreshToken", loginResponse.getRefreshToken());
            response.put("expiresIn", loginResponse.getExpiresIn());
            response.put("userId", loginResponse.getUserId());
            response.put("username", loginResponse.getUsername());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 刷新 Token — 无需 Authorization header，直接传 refreshToken
     * 成功后返回新的 access + 新的 refresh（Token Rotation）
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            LoginResponse loginResponse = authService.refresh(request.getRefreshToken());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "刷新成功");
            response.put("token", loginResponse.getAccessToken());
            response.put("accessToken", loginResponse.getAccessToken());
            response.put("refreshToken", loginResponse.getRefreshToken());
            response.put("expiresIn", loginResponse.getExpiresIn());
            response.put("userId", loginResponse.getUserId());
            response.put("username", loginResponse.getUsername());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * 登出 — 需要登录态（Authorization: Bearer accessToken）
     * 操作：access 进黑名单 + 删除该用户的 refresh
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication,
                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        String username = null;
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            username = user.getUsername();
        }
        if (username == null && authentication != null) {
            username = authentication.getName();
        }
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        authService.logout(username, accessToken);

        response.put("success", true);
        response.put("message", "已退出登录");
        return ResponseEntity.ok(response);
    }
}
