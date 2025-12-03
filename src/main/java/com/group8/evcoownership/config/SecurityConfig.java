package com.group8.evcoownership.config;

import com.group8.evcoownership.filter.JwtAuthenticationEntryPoint;
import com.group8.evcoownership.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity //Bật hỗ trợ annotation như @PreAuthorize trên controller/service
@RequiredArgsConstructor // thằng loombok sẽ tự tạo constructor cho tất cả final fields
// (jwtAuthFilter, jwtAuthenticationEntryPoint)
public class SecurityConfig {

    // khai báo bean PasswordEncoder để dùng toàn ứng dụng (vd: luu password)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();//dùng Bcrypt để hash pasword
    }

    // Các thuoc tinh inject qua constructor do @RequiredArgsConstructor
    private final JwtAuthenticationFilter jwtAuthFilter; // custom filter kiểm tra token JWT và set Authentication
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // xử lý error trả về 401 khi chưa auth

    @Bean // khai báo bean SecurityFilterChain cấu hình chính cho thằng Spring Security
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // TẮT CSRF protection vì backend là stateless API (thường dùng với token)
                .cors(Customizer.withDefaults())  // bật CORS và dùng CorsConfigurationSource bean bên dưới
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Đặt session management là STATELESS => không dùng HttpSession, phù hợp với JWT
                .authorizeHttpRequests(auth -> auth
                        // 1. OPTIONS requests (CORS preflight) - ĐẶT ĐẦU
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. Public endpoints (thực sự không cần login)
                        .requestMatchers(
                                "/swagger-ui/**",          // Swagger UI cho tài liệu API
                                "/v3/api-docs/**",         // OpenAPI JSON
                                "/api/auth/**",            // Login/Register/refresh/forgot-password...
                                "/api/auth/vnpay/**",      // VNPAY public endpoints
                                "/api/deposits/deposit-callback", // callback cho deposit (VNPAY redirect)
                                "/api/test/**",            // test endpoint (giữ public cho dev)
                                "/ws/**"                   // SockJS/WebSocket handshake + info endpoint
                        ).permitAll()

                        // 3. Role-based endpoints (coarse-grained, chi tiết hơn dùng @PreAuthorize)
                        .requestMatchers("/api/staff/**").hasAnyRole("STAFF", "ADMIN")
                        // Feedback contract endpoints cho group admin (Co-owner), dù đang nằm trong AdminContractController
                        .requestMatchers("/api/admin/contracts/feedbacks/**").hasRole("CO_OWNER")
                        // Các endpoint admin còn lại
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/vehicle-checks/**")
                        .hasAnyRole("CO_OWNER", "ADMIN", "STAFF", "TECHNICIAN")

                        // 4. Các endpoint còn lại yêu cầu đã đăng nhập,
                        // phân quyền chi tiết dựa trên @PreAuthorize ở controller/service
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                // Khi thiếu auth => chuyển cho jwtAuthenticationEntryPoint xử lý (thường trả 401)
                                .accessDeniedHandler((req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN))
                        // Khi có auth nhưng không đủ quyền => trả 403 (FORBIDDEN)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        // Thêm custom jwtAuthFilter trước UsernamePasswordAuthenticationFilter để kiểm tra token JWT trước

        return http.build(); // build và trả về SecurityFilterChain bean
    }


    @Bean // Bean để cấu hình chính sách CORS cho toàn ứng dụng
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:3000", // local dev frontend
                "https://ev-co-ownership-cost-sharing-system.vercel.app", // deployed app
                "https://*.vercel.app" // bất kỳ subdomain vercel.app (pattern)
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Cho phép các phương thức HTTP này từ origin được phép
        configuration.setAllowedHeaders(List.of("*")); // Cho phép tất cả header gửi lên (Authorization, Content-Type...)
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        // Header nào frontend có thể đọc từ response (ví dụ token trả về trong Authorization)
        configuration.setAllowCredentials(true); // cho phép gửi cookie / credential (nếu có) — lưu ý browser yêu cầu origin cụ thể (không '*')

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // áp dụng cấu hình cho tất cả endpoint
        return source; // trả về CorsConfigurationSource bean để .cors(...) sử dụng
    }
}
