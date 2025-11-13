package com.group8.evcoownership.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group8.evcoownership.dto.ErrorResponseDTO;
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
import org.springframework.http.MediaType;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter { // Base class của Spring cho filter chỉ chạy 1 lần mỗi request

    @Autowired
    private JwtUtil jwtUtil; // Tiện ích xác thực, giải mã, trích xuất giá trị từ JWT

    @Autowired
    private UserRepository userRepository; // Repository để truy vấn user theo email có trong JWT

    @Autowired
    private LogoutService logoutService; // Quản lý blacklist token (token bị revoke khi logout)

    private final ObjectMapper objectMapper; // Dùng để serialize ErrorResponseDTO thành JSON

    public JwtAuthenticationFilter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Đọc header Authorization từ request (theo chuẩn Bearer)
        final String authHeader = request.getHeader("Authorization");

        // Nếu header không có hoặc không bắt đầu bằng "Bearer ",
        // thì bỏ qua xác thực JWT và chuyển tiếp sang filter tiếp theo (cho phép vào các route public)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Cắt "Bearer " để lấy chuỗi JWT thực sự
        String token = authHeader.substring(7);

        // Kiểm tra token rỗng (có thể toàn khoảng trắng); nếu lỗi, trả về 401 ngay
        if (token.trim().isEmpty()) {
            log.warn("Empty token detected"); // Ghi cảnh báo
            sendErrorResponse(response, request, "Invalid token");
            return;
        }

        // Kiểm tra nếu token đã bị revoke (logout) – ngăn dùng lại token cũ
        if (logoutService.isTokenBlacklisted(token)) {
            sendErrorResponse(response, request, "Invalid token");
            return;
        }

        try {
            // Xác thực token (chữ ký hợp lệ, còn hạn, cấu trúc đúng, issuer đúng...)
            if (jwtUtil.validateToken(token)) {
                // Trích xuất email từ token nếu token hợp lệ
                String email = jwtUtil.extractEmail(token);
                log.info("Extracted email from token: {}", email);
                // Truy vấn user theo email; nếu không tồn tại thì controller sau sẽ xử lý
                User user = userRepository.findByEmail(email).orElse(null);

                // Nếu user tồn tại, tạo authority cho role của user
                if (user != null) {
                    log.info("User found: {} with role: {}", user.getEmail(), user.getRole().getRoleName());
                    // Authority theo format ROLE_XXX, phục vụ Spring Security check role
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                            "ROLE_" + user.getRole().getRoleName().toString()
                    );
                    // Tạo đối tượng Authentication: truyền email làm principal, không cần mật khẩu, truyền role authority
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), // principal
                            null, // credentials luôn null vì không cần kiểm tra mật khẩu trong JWT flow
                            Collections.singletonList(authority) // danh sách quyền truy cập
                    );
                    // Gán Authentication vào SecurityContextHolder => user được coi là authenticated từ đây đến cuối request
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    // Nếu user không tìm được, trả về 401 ngay
                    log.error("User not found for email: {}", email);
                    sendErrorResponse(response, request, "Invalid token");
                    return;
                }
            } else {
                // Token hợp lệ về mặt kỹ thuật nhưng verify nội dung thất bại
                log.warn("Invalid token: validation failed");
                sendErrorResponse(response, request, "Invalid token");
                return;
            }

        } catch (ExpiredJwtException e) {
            // Token hết hạn: trả về 401 ngay với message "Invalid token"
            log.warn("JWT token expired: {}", e.getMessage());
            sendErrorResponse(response, request, "Invalid token");
            return;

        } catch (MalformedJwtException e) {
            // Token sai cấu trúc: không giải mã được
            log.warn("Invalid JWT token: {}", e.getMessage());
            sendErrorResponse(response, request, "Invalid token");
            return;

        } catch (SignatureException e) {
            // Chữ ký token không trùng với public key hoặc secret configure
            log.warn("Invalid JWT signature: {}", e.getMessage());
            sendErrorResponse(response, request, "Invalid token");
            return;

        } catch (IllegalArgumentException e) {
            // Các trường hợp truyền token null/rỗng (chặt chẽ hơn ngoài ở trên)
            log.warn("JWT token is empty: {}", e.getMessage());
            sendErrorResponse(response, request, "Invalid token");
            return;

        } catch (Exception e) {
            // Lỗi hệ thống hoặc không lường trước
            log.error("Cannot set user authentication: {}", e.getMessage());
            sendErrorResponse(response, request, "Invalid token");
            return;
        }
        // Hoàn thành filter, tiếp tục chuỗi filter tới controller hoặc các filter tiếp theo
        filterChain.doFilter(request, response);
    }

    /**
     * Gửi response 401 với message "Invalid token"
     */
    private void sendErrorResponse(HttpServletResponse response, HttpServletRequest request, String message) throws IOException {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Bỏ qua filter tại các đường dẫn auth/login/register/refresh/docs/swagger – những nơi không yêu cầu JWT
        String path = request.getServletPath();
        return path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger")
                || path.startsWith("/swagger-ui");
    }
}

