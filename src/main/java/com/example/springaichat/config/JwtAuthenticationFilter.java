package com.example.springaichat.config;

import com.example.springaichat.entity.User;
import com.example.springaichat.repository.UserRepository;
import com.example.springaichat.service.TokenStoreService;
import com.example.springaichat.util.JwtUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * JWT 认证过滤器 — 双 Token 方案
 *
 * <p>本过滤器只允许 <b>access</b> 类型的 Token 建立登录态；
 * refresh Token 必须走专属接口 {@code /api/auth/refresh}，绝不允许当 access 用。
 * 登出后的 access Token 会在 Redis 黑名单里，命中直接拒绝。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenStoreService tokenStoreService;

    /**
     * 用户信息缓存：JWT 每请求校验通过后不必再查一次 user 表。
     * 5 分钟过期，删除用户的场景最多延迟 5 分钟生效。
     */
    private final Cache<String, Optional<User>> userCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserRepository userRepository,
                                   TokenStoreService tokenStoreService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tokenStoreService = tokenStoreService;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        logger.debug("JWT filter request path: " + request.getRequestURI());

        // 没有 Authorization header 或者不是 Bearer，直接放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            // 一次验签拿到全部声明，避免为 username / type / jti 反复解析
            Claims claims = jwtUtil.parseClaims(jwt);
            username = claims.getSubject();

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 1. type 必须是 access（防止 refresh Token 被拿来当 access 调业务接口）
                // 2. 黑名单校验：登出 / 掉线的 access 不能再用
                boolean typeOk = JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class));
                String jti = claims.getId();

                if (typeOk && !tokenStoreService.isAccessTokenBlacklistedByJti(jti)) {
                    Optional<User> userOptional = userCache.get(username, userRepository::findByUsername);
                    if (userOptional.isPresent()) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userOptional.get(),
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.debug("JWT filter authenticated user: " + username);
                    }
                } else {
                    logger.warn("JWT filter rejected token username=" + username + ", typeOk=" + typeOk);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 过期 / 签名不合法 / 格式错误属于正常业务场景，不打印堆栈
            logger.debug("Invalid JWT: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Cannot set user authentication: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
