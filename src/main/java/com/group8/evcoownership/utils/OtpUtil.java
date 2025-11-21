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
// Thằng otpUtil này sẽ quản lý OTP trên memory
// Nó sẽ sinh ra OTP có 6 số, lưu kèm thời gian hết hạn
// Giới hạn số lần nhập sai, khóa tạm tài khoản nếu vượt quá
// Cho hàm verify đếm số lần còn lại, cleanup OTP và failed attempts theo lịch
public class OtpUtil {

    // Thời gian sống của 1 OTP (phút)
    private static final int OTP_EXPIRATION_MINUTES = 5;
    // Số lần nhập sai tối đa trước khi bị khóa
    private static final int MAX_FAILED_ATTEMPTS = 3;
    // Thời gian khóa (phút) sau khi vượt quá số lần nhập sai
    private static final int LOCKOUT_MINUTES = 15;

    // Lưu OTP theo email: email -> (mã OTP, thời điểm hết hạn)
    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    // Lưu số lần nhập sai và thời điểm bị khóa theo email
    private final Map<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();
    // Random để sinh OTP 6 chữ số
    private final Random random = new Random();

    // Ở đây dùng private record để data là immutable
    // khi tạo mới nó cần thiết voi cac map lưu tren memory
    // , cache tạm thời tránh bug khi nhiều client truy cập
    // Record lưu thông tin OTP: giá trị OTP + thời điểm hết hạn
    private record OtpEntry(String otp, LocalDateTime expireAt) {}

    // Record lưu số lần nhập sai + thời điểm bị khóa tới
    private record FailedAttempt(int count, LocalDateTime lockoutUntil) {}

    // ========= Generate Otp =========
    public String generateOtp(String email) {
        // 1. Kiểm tra xem email này có đang bị khóa do nhập sai quá nhiều không
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt != null && attempt.lockoutUntil.isAfter(LocalDateTime.now())) {
            // Nếu vẫn còn trong khoảng thời gian lockout thì không cho sinh OTP mới
            long minutesLeft = java.time.Duration.between(
                    LocalDateTime.now(),
                    attempt.lockoutUntil
            ).toMinutes();
            throw new RuntimeException(
                    "The account is temporarily locked due to too many incorrect OTP attempts. Please try again in "
                            + minutesLeft + " minutes"
            );
        }

        // 2. Sinh OTP 6 chữ số, padding 0 ở đầu nếu cần (vd: 000123)
        String otp = String.format("%06d", random.nextInt(1000000));

        // 3. Tính thời điểm hết hạn OTP = now + 5 phút
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES);

        // 4. Lưu vào cache theo email
        otpCache.put(email, new OtpEntry(otp, expireAt));

        // Log nhẹ để debug khi cần (không log OTP để tránh lộ)
        log.info("Generated OTP for email: {}", email);
        return otp;
    }

    // ========= Verify OTP =========
    public boolean verifyOtp(String email, String otp) {
        // 1. Kiểm tra xem email có đang bị khóa không (do nhập sai quá số lần cho phép)
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt != null && attempt.lockoutUntil.isAfter(LocalDateTime.now())) {
            // Đang bị lockout thì từ chối luôn
            return false;
        }

        // 2. Lấy OTP đang lưu trong cache (theo email)
        OtpEntry entry = otpCache.get(email);
        if (entry == null) {
            // Không tìm thấy OTP: có thể chưa request hoặc đã bị xoá
            recordFailedAttempt(email); // tăng số lần nhập sai
            return false;
        }

        // 3. Kiểm tra OTP đã hết hạn chưa
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            // Hết hạn thì xoá luôn, yêu cầu user xin OTP mới
            otpCache.remove(email);
            return false;
        }

        // 4. So sánh giá trị OTP nhập vào với OTP đã lưu
        boolean valid = entry.otp.equals(otp);
        if (valid) {
            // Đúng OTP:
            // Xoá OTP khỏi cache để tránh dùng lại
            // Xoá record failedAttempts để reset lại số lần sai
            otpCache.remove(email);
            failedAttempts.remove(email);
        } else {
            // Sai OTP:
            // Ghi nhận thêm 1 lần sai
            // Có thể dẫn tới khóa tạm nếu vượt ngưỡng
            recordFailedAttempt(email);
        }

        return valid;
    }

    // ========= Ghi nhận 1 lần nhập sai OTP =========
    private void recordFailedAttempt(String email) {
        // Lấy số lần sai hiện tại (nếu có)
        FailedAttempt currentAttempt = failedAttempts.get(email);
        int newCount = (currentAttempt != null) ? currentAttempt.count + 1 : 1;

        if (newCount >= MAX_FAILED_ATTEMPTS) {
            // Nếu vượt số lần cho phép: khoá email trong LOCKOUT_MINUTES
            LocalDateTime lockoutUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            failedAttempts.put(email, new FailedAttempt(newCount, lockoutUntil));
        } else {
            // Nếu chưa đến ngưỡng khoá:
            // - Chỉ tăng count
            // - lockoutUntil = now (dùng để tính remainingAttempts, không thực sự bị khóa)
            failedAttempts.put(email, new FailedAttempt(newCount, LocalDateTime.now()));
        }
    }

    // ========= Lấy số lần nhập sai còn lại cho 1 email =========
    public int getRemainingAttempts(String email) {
        FailedAttempt attempt = failedAttempts.get(email);
        if (attempt == null) {
            // Chưa từng nhập sai => còn đủ MAX_FAILED_ATTEMPTS
            return MAX_FAILED_ATTEMPTS;
        }
        if (attempt.lockoutUntil.isAfter(LocalDateTime.now())) {
            // Nếu đang bị khóa thì trả 0 (không còn lượt)
            return 0;
        }
        // Ngược lại: trả về MAX - count, nhưng không âm
        return Math.max(0, MAX_FAILED_ATTEMPTS - attempt.count);
    }

    // ========= Invalidate OTP theo email (cleanup thủ công) =========
    // Dùng khi gửi email OTP lỗi hoặc user hủy flow, cần xoá OTP cũ
    public void invalidateOtp(String email) {
        otpCache.remove(email);
    }

    // ========= Scheduled cleanup =========
    // Job tự động dọn rác OTP / failedAttempts mỗi 10 phút
    @Scheduled(fixedRate = 600000) // 10 phút
    public void cleanupExpiredEntries() {
        LocalDateTime now = LocalDateTime.now();

        // Xóa OTP đã hết hạn khỏi cache
        otpCache.entrySet().removeIf(entry ->
                entry.getValue().expireAt.isBefore(now)
        );

        // Xóa failedAttempts nếu:
        // - lockoutUntil < now (hết thời gian khóa) và
        // - số lần sai < MAX_FAILED_ATTEMPTS (tức chỉ là record cũ, không còn tác dụng khóa)
        failedAttempts.entrySet().removeIf(entry ->
                entry.getValue().lockoutUntil.isBefore(now)
                        && entry.getValue().count < MAX_FAILED_ATTEMPTS
        );
    }
}
