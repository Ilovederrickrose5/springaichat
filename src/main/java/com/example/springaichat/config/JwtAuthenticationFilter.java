package com.example.springaichat.config;

import com.example.springaichat.entity.User;
import com.example.springaichat.repository.UserRepository;
import com.example.springaichat.util.JwtUtil;
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
import java.util.Collections;
import java.util.Optional;

/**
 * JWT认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
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

        logger.info("JWT filter request path: " + request.getRequestURI());
        logger.info("JWT filter dispatcher type: " + request.getDispatcherType());
        logger.info("JWT filter Origin header: " + request.getHeader("Origin"));
        logger.info("JWT filter Access-Control-Request-Method: " + request.getHeader("Access-Control-Request-Method"));
        logger.info("JWT filter Access-Control-Request-Headers: " + request.getHeader("Access-Control-Request-Headers"));
        logger.info("JWT filter Authorization header: " + authHeader);

        // 如果没有Authorization header或者不是Bearer token，直接放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("JWT filter skipped because Authorization header is missing or invalid");
            filterChain.doFilter(request, response);
            return;
        }

        // 提取JWT token
        jwt = authHeader.substring(7);
        
        try {
            username = jwtUtil.extractUsername(jwt);
            logger.info("JWT filter extracted username: " + username);

            // 如果用户名不为空且当前没有认证信息
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> userOptional = userRepository.findByUsername(username);

                if (userOptional.isPresent() && jwtUtil.validateToken(jwt, username)) {
                    // 创建认证令牌
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userOptional.get(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置到SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.info("JWT filter authenticated user: " + username);
                } else {
                    logger.warn("JWT filter rejected token for username: " + username);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }
}
