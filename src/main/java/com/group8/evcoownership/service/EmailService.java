package com.group8.evcoownership.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Xác minh đăng ký tài khoản EV Co-ownership");
        message.setText("Mã OTP của bạn là: " + otp + "\nOTP sẽ hết hạn sau 3 phút.");
        mailSender.send(message);
    }

    // Dùng cho lời mời tham gia nhóm
    public void sendInvitationEmail(
            String to,
            String groupName,
            String inviterName,
            String token,
            String otp,
            java.time.LocalDateTime expiresAt,
            java.math.BigDecimal suggestedPercentage, // có thể null
            String acceptUrl // URL FE: ví dụ https://app.xyz/invitations/accept?token=...
    ) {
        var percentLine = (suggestedPercentage != null)
                ? "Suggested ownership: %s%%\n".formatted(suggestedPercentage.stripTrailingZeros().toPlainString())
                : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("EV Co-ownership — Invitation to Join: " + groupName);
        message.setText("""
            You’ve been invited by %s to join the group: %s

            Accept link: %s
            One-time password (OTP): %s

            %sInvitation expires: %s (UTC)

            If you didn’t initiate this, please ignore this email.
            """.formatted(
                inviterName, groupName,
                acceptUrl,
                otp,
                percentLine,
                expiresAt // ISO-8601 or format as needed
        ));
        mailSender.send(message);
    }

    // ========== THÊM METHOD NÀY ==========
    public void sendPasswordResetOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Đặt lại mật khẩu - Mã OTP");
        message.setText(
                "Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản EV Co-ownership.\n\n" +
                        "Mã OTP của bạn là: " + otp + "\n\n" +
                        "⚠️ Lưu ý: Mã OTP này có hiệu lực trong 5 phút.\n\n" +
                        "Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.\n\n" +
                        "---\n" +
                        "Email này được gửi tự động. Vui lòng không trả lời."
        );
        mailSender.send(message);
    }
    // ========== KẾT THÚC ==========
}
