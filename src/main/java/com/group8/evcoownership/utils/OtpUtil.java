package com.group8.evcoownership.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OtpUtil {
    private static final int OTP_EXPIRATION_MINUTES = 5;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCKOUT_MINUTES = 15;

    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private final Map<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private record OtpEntry(String otp, LocalDateTime expireAt) {
    }

    private record FailedAttempt(int count, LocalDateTime lockoutUntil) {
    }

    // ================= GENERATE OTP =================
    public String generateOtp(String email) {
        // Kiểm tra xem email có bị khóa không
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt != null && attempt.lockoutUntil.isAfter(LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(
                    LocalDateTime.now(),
                    attempt.lockoutUntil
            ).toMinutes();
            log.warn("Email {} is locked out for {} more minutes", email, minutesLeft);
            throw new RuntimeException(
                    "The account is temporarily locked due to too many incorrect OTP attempts. Please try again in " + minutesLeft + " minutes"
            );
        }

        String otp = String.format("%06d", random.nextInt(1000000));
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES);
        otpCache.put(email, new OtpEntry(otp, expireAt));

        log.info("Generated OTP for email: {} (expires at: {})", email, expireAt);
        return otp;
    }

    // ================= VERIFY OTP =================
    public boolean verifyOtp(String email, String otp) {
        log.info("Verifying OTP for email: {}", email);

        // Kiểm tra lockout
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt != null && attempt.lockoutUntil.isAfter(LocalDateTime.now())) {
            log.warn("Verification blocked - email {} is locked out", email);
            return false;
        }

        // Lấy OTP từ cache
        OtpEntry entry = otpCache.get(email);
        if (entry == null) {
            log.error("No OTP found for email: {}", email);
            recordFailedAttempt(email);
            return false;
        }

        // Kiểm tra hết hạn
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            log.error("OTP expired for email: {}", email);
            otpCache.remove(email);
            return false;
        }

        // Kiểm tra OTP khớp
        boolean valid = entry.otp.equals(otp);
        if (valid) {
            log.info("OTP verified successfully for email: {}", email);
            otpCache.remove(email);
            failedAttempts.remove(email); // Xóa failed attempts khi thành công
        } else {
            log.error("Invalid OTP for email: {}", email);
            recordFailedAttempt(email);
        }

        return valid;
    }

    // ================= RECORD FAILED ATTEMPT =================
    private void recordFailedAttempt(String email) {
        FailedAttempt currentAttempt = failedAttempts.get(email);
        int newCount = (currentAttempt != null) ? currentAttempt.count + 1 : 1;

        if (newCount >= MAX_FAILED_ATTEMPTS) {
            LocalDateTime lockoutUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            failedAttempts.put(email, new FailedAttempt(newCount, lockoutUntil));
            log.warn("Email {} locked out until {} due to {} failed attempts",
                    email, lockoutUntil, newCount);
        } else {
            failedAttempts.put(email, new FailedAttempt(newCount, LocalDateTime.now()));
            log.info("Failed attempt {} for email: {}", newCount, email);
        }
    }

    // ================= GET REMAINING ATTEMPTS =================
    public int getRemainingAttempts(String email) {
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt == null) return MAX_FAILED_ATTEMPTS;
        if (attempt.lockoutUntil.isAfter(LocalDateTime.now())) return 0;
        return Math.max(0, MAX_FAILED_ATTEMPTS - attempt.count);
    }

    // ================= INVALIDATE OTP (CLEANUP METHOD) - THÊM MỚI =================

    /**
     * Remove OTP data for given email
     * Used for cleanup when email sending fails or user cancels registration
     */
    public void invalidateOtp(String email) {
        OtpEntry removed = otpCache.remove(email);
        if (removed != null) {
            log.info("Invalidated OTP for email: {}", email);
        }
    }

    // ================= CHECK IF OTP EXISTS - THÊM MỚI =================

    /**
     * Check if OTP exists for given email
     */
    public boolean hasOtp(String email) {
        OtpEntry entry = otpCache.get(email);
        if (entry == null) {
            return false;
        }
        // Check if expired
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            otpCache.remove(email);
            return false;
        }
        return true;
    }

    // ================= SCHEDULED CLEANUP =================
    @Scheduled(fixedRate = 600000) // 10 phút
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();

        // Xóa OTP hết hạn
        otpCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expireAt.isBefore(now);
            if (expired) {
                log.debug("Removed expired OTP for email: {}", entry.getKey());
            }
            return expired;
        });

        // Xóa failed attempts hết hạn
        failedAttempts.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().lockoutUntil.isBefore(now)
                    && entry.getValue().count < MAX_FAILED_ATTEMPTS;
            if (expired) {
                log.debug("Removed expired failed attempts for email: {}", entry.getKey());
            }
            return expired;
        });

        log.debug("Cleanup completed. OTP cache size: {}, Failed attempts size: {}",
                otpCache.size(), failedAttempts.size());
    }
}
