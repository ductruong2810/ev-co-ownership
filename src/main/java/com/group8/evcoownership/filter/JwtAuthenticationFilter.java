package com.group8.evcoownership.filter;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.LogoutService;
import com.group8.evcoownership.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j //slf4j nay de hien thi log errors
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LogoutService logoutService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        //kiem tra token trống
        if (token == null || token.trim().isEmpty()) {
            log.warn("Empty token detected");
            request.setAttribute("jwt_error", "Token không được để trống");
            filterChain.doFilter(request, response);
            return;
        }

        // ========== CHECK BLACKLIST ==========
        if (logoutService.isTokenBlacklisted(token)) {
            request.setAttribute("jwt_error", "Token đã bị thu hồi. Vui lòng đăng nhập lại");
            filterChain.doFilter(request, response);
            return;
        }

        // ========== VALIDATE TOKEN VỚI TRY-CATCH ==========
        try {
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractEmail(token);
                log.info("Extracted email from token: {}", email);
                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null) {
                    log.info("User found: {} with role: {}", user.getEmail(), user.getRole().getRoleName());
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                            "ROLE_" + user.getRole().getRoleName().toString()
                    );

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            Collections.singletonList(authority)
                    );

                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    log.error("User not found for email: {}", email);
                    request.setAttribute("jwt_error", "Người dùng không tồn tại");
                }
            } else {
                request.setAttribute("jwt_error", "Token không hợp lệ");
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token đã hết hạn. Vui lòng đăng nhập lại");

        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token không đúng định dạng");

        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            request.setAttribute("jwt_error", "Chữ ký token không hợp lệ");

        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token không được để trống");

        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            request.setAttribute("jwt_error", "Xác thực token thất bại");
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger")
                || path.startsWith("/swagger-ui");
    }
}
