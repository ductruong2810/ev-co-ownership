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
@Slf4j
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

        if (token.trim().isEmpty()) {
            log.warn("Empty token detected");
            request.setAttribute("jwt_error", "Token must not be empty");
            filterChain.doFilter(request, response);
            return;
        }

        if (logoutService.isTokenBlacklisted(token)) {
            request.setAttribute("jwt_error", "Token has been revoked. Please log in again");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractEmail(token);
                log.info("Extracted email from token: {}", email);
                User user = userRepository.findByEmail(email).orElse(null);

                log.info("User found: {} with role: {}", user.getEmail(), user.getRole().getRoleName());
                log.info("Authority created: ROLE_{}", user.getRole().getRoleName()); // ← Thêm dòng này
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
                    request.setAttribute("jwt_error", "User does not exist");
                }
            } else {
                request.setAttribute("jwt_error", "Invalid token");
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token has expired. Please log in again");

        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token is not in the correct format");

        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            request.setAttribute("jwt_error", "Invalid token signature");

        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token must not be empty");

        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            request.setAttribute("jwt_error", "Token authentication failed");
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger")
                || path.startsWith("/swagger-ui");
    }
}
