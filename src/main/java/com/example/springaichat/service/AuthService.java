package com.example.springaichat.service;

import com.example.springaichat.dto.LoginRequest;
import com.example.springaichat.dto.LoginResponse;
import com.example.springaichat.dto.RegisterRequest;
import com.example.springaichat.entity.User;
import com.example.springaichat.repository.UserRepository;
import com.example.springaichat.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务（双 Token 方案）
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenStoreService tokenStoreService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TokenStoreService tokenStoreService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenStoreService = tokenStoreService;
    }

    /**
     * 用户注册
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        return userRepository.save(user);
    }

    /**
     * 用户登录：返回 accessToken + refreshToken
     * 同一用户多次登录，refresh Token 以后登录的为准（前登录被踢）
     */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        long expiresIn = jwtUtil.getAccessExpiration() / 1000;

        tokenStoreService.saveRefreshToken(user.getUsername(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, expiresIn, user.getId(), user.getUsername());
    }

    /**
     * 刷新 access Token：
     *   1. 校验 refresh Token 签名 + 过期 + type 必须是 refresh
     *   2. 校验 Redis 中该 username 绑定的 refreshToken 必须等于当前传入的（防止复用）
     *   3. 通过则重新生成一对 access + refresh（refresh rotation 机制，旧 refresh 立刻失效）
     */
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("refresh Token 不能为空");
        }
        if (!jwtUtil.isValidWithoutUsername(refreshToken, JwtUtil.TYPE_REFRESH)) {
            throw new RuntimeException("refresh Token 无效或已过期");
        }
        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new RuntimeException("refresh Token 解析失败");
        }

        if (!tokenStoreService.isValidRefreshToken(username, refreshToken)) {
            logger.warn("Refresh token mismatch or expired for user={}", username);
            throw new RuntimeException("refresh Token 无效或已在其他设备登录");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String newAccessToken = jwtUtil.generateAccessToken(username);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);
        long expiresIn = jwtUtil.getAccessExpiration() / 1000;

        tokenStoreService.saveRefreshToken(username, newRefreshToken);

        return new LoginResponse(newAccessToken, newRefreshToken, expiresIn, user.getId(), user.getUsername());
    }

    /**
     * 登出：
     *   1. access Token 加入黑名单（防止登出后 Token 还能用）
     *   2. 删除该用户绑定的 refresh Token（防止 refresh 继续换新的 Token）
     */
    public void logout(String username, String accessToken) {
        if (username == null) {
            return;
        }
        tokenStoreService.deleteRefreshToken(username);
        if (accessToken != null && !accessToken.isBlank()) {
            tokenStoreService.blacklistAccessToken(accessToken);
        }
        logger.info("User logged out, username={}", username);
    }
}
