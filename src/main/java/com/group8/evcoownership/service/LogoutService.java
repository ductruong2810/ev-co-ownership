package com.group8.evcoownership.service;

import com.group8.evcoownership.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LogoutService {

    @Autowired
    private JwtUtil jwtUtil;

    // Lưu token blacklist trong memory
    private final Map<String, LocalDateTime> tokenBlacklist = new ConcurrentHashMap<>();

    /**
     * Thêm token vào blacklist khi logout
     */
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to blacklist empty token");
            return;
        }

        try {
            LocalDateTime expirationTime = jwtUtil.getExpirationFromToken(token);
            tokenBlacklist.put(token, expirationTime);
            log.info("Token added to blacklist, expires at: {}", expirationTime);
        } catch (Exception e) {
            log.error("Error blacklisting token: {}", e.getMessage());
            throw new RuntimeException("Không thể đăng xuất. Token không hợp lệ.");
        }
    }

    /**
     * Kiểm tra token có trong blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return tokenBlacklist.containsKey(token);
    }

    /**
     * Dọn dẹp tokens đã hết hạn mỗi 1 giờ
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        int beforeSize = tokenBlacklist.size();
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int afterSize = tokenBlacklist.size();
        int removed = beforeSize - afterSize;

        if (removed > 0) {
            log.info("Cleaned up {} expired tokens from blacklist. Current size: {}", removed, afterSize);
        }
    }
}
