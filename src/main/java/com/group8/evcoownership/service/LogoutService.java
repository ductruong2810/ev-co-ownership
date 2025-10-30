package com.group8.evcoownership.service;

import com.group8.evcoownership.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    @Autowired
    private JwtUtil jwtUtil;

    // Blacklist lưu token đã logout
    // Key: token, Value: expiration date
    private final Map<String, Date> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Logout - Thêm token vào blacklist
     */
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }

        try {
            // Validate token trước khi blacklist
            if (!jwtUtil.validateToken(token)) {
                throw new IllegalArgumentException("Token is invalid or has expired");
            }

            // Lấy expiration date của token
            Date expirationDate = jwtUtil.extractExpiration(token);

            // Thêm vào blacklist
            blacklistedTokens.put(token, expirationDate);

            String email = jwtUtil.extractEmail(token);
            log.info("User {} logged out successfully. Token blacklisted.", email);

            // Cleanup expired tokens (optional - chạy định kỳ)
            cleanupExpiredTokens();

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            throw new RuntimeException("Unable to logout: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra token có bị blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokens.containsKey(token);
    }

    /**
     * Cleanup tokens đã hết hạn khỏi blacklist
     * (Gọi định kỳ để tiết kiệm memory)
     */
    private void cleanupExpiredTokens() {
        Date now = new Date();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().before(now));
        log.debug("Cleaned up expired tokens. Remaining blacklisted: {}", blacklistedTokens.size());
    }

    /**
     * Get số lượng token trong blacklist (for debugging)
     */
    public int getBlacklistedTokenCount() {
        return blacklistedTokens.size();
    }
}
