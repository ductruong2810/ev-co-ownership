package com.group8.evcoownership.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group8.evcoownership.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Đăng ký bean với Spring, để Spring Security tự động dùng khi phát sinh lỗi xác thực.
@Component
// Interface cung cấp cho Spring Security chỗ "entry point" cuối cùng xử lý lỗi xác thực.
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper; // Dùng để chuyển Java object sang JSON,
                                             // đảm bảo trả lỗi chuẩn REST API.

    // Constructor: Khởi tạo ObjectMapper và cấu hình hỗ trợ kiểu ngày/giờ (JavaTimeModule)
    public JwtAuthenticationEntryPoint() {
        this.objectMapper = new ObjectMapper(); // Khởi tạo object mapper mặc định cho JSON.
        this.objectMapper.registerModule(new JavaTimeModule()); // Đăng ký module hỗ trợ serialize các kiểu
                                                                // LocalDateTime trong DTO sang JSON (giải quyết lỗi thời gian).
    }

    // Phương thức này được gọi mỗi khi Security phát hiện
    // request chưa hoặc không xác thực hợp lệ.
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Lấy thông điệp lỗi đã gán trước đó bởi filter JWT
        // (ví dụ "Token expired", "Token bị logout",...) để trả cho client.
        String errorMessage = (String) request.getAttribute("jwt_error");

        // Nếu không có lỗi cụ thể thì mặc định
        // trả thông báo yêu cầu đăng nhập.
        if (errorMessage == null) {
            errorMessage = "You need to log in to access this resource";
        }

        // Tạo response DTO (ErrorResponseDTO) vừa có mã lỗi, tiêu đề,
        // chi tiết thông điệp, URI đã truy cập -> trả về cho front-end.
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpServletResponse.SC_UNAUTHORIZED, // 401: Unauthorized
                "Unauthorized", // Tiêu đề lỗi
                errorMessage, // Nội dung lỗi (cụ thể hoặc mặc định)
                request.getRequestURI() // Đường dẫn endpoint client vừa truy cập
        );

        // Set các thuộc tính HTTP trả về: mã lỗi, kiểu dữ liệu JSON,
        // mã hóa UTF-8, và serialize ErrorResponse thành JSON.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Phản hồi mã HTTP 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // Định dạng trả về là JSON
        response.setCharacterEncoding("UTF-8"); // Đảm bảo không lỗi ký tự tiếng Việt
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse)); // Serialize DTO thành JSON, ghi ra body cho client nhận
    }
}


