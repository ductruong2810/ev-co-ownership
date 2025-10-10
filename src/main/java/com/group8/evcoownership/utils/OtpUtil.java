package com.group8.evcoownership.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OtpUtil {
    private static final int OTP_EXPIRATION_MINUTES = 3;
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private record OtpEntry(String otp, LocalDateTime expireAt) {}

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(999999));
        otpCache.put(email, new OtpEntry(otp, LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES)));
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = otpCache.get(email);
        if (entry == null) return false;
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            otpCache.remove(email);
            return false;
        }
        boolean valid = entry.otp.equals(otp);
        if (valid) otpCache.remove(email);
        return valid;
    }
}
