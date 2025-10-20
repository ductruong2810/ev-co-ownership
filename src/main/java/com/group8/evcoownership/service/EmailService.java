package com.group8.evcoownership.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // ← THÊM DÒNG NÀY
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Gửi OTP email cho đăng ký tài khoản
     */
    public void sendOtpEmail(String to, String fullName, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // URL redirect đến trang verify của frontend
            String verifyUrl = String.format("%s/verify-otp?email=%s&type=REGISTRATION",
                    frontendUrl, to);

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("otp", otp);
            context.setVariable("expiryMinutes", 3);
            context.setVariable("verifyUrl", verifyUrl);

            String htmlContent = templateEngine.process("otp-registration-email", context);

            helper.setTo(to);
            helper.setSubject("Xác minh đăng ký tài khoản EV Co-ownership");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP registration email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: {}", to, e);
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.");
        }
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
                ? "Tỷ lệ sở hữu gợi ý: %s%%\n".formatted(suggestedPercentage.stripTrailingZeros().toPlainString())
                : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Lời mời tham gia nhóm EV: " + groupName);
        message.setText("""
                Bạn được %s mời tham gia nhóm: %s

                Link chấp nhận: %s
                Mã OTP: %s

                %sHạn lời mời: %s (UTC)
                
                Nếu bạn không thực hiện, vui lòng bỏ qua email này.
                """.formatted(
                inviterName, groupName,
                acceptUrl,
                otp,
                percentLine,
                expiresAt // hiển thị ISO-8601, hoặc format lại nếu muốn
        ));
        mailSender.send(message);
    }

    // ========== THÊM METHOD NÀY ==========
    /**
     * Gửi OTP email cho đặt lại mật khẩu
     */
    public void sendPasswordResetOtpEmail(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // URL redirect đến trang verify của frontend
            String verifyUrl = String.format("%s/verify-otp?email=%s&type=PASSWORD_RESET",
                    frontendUrl, to);

            Context context = new Context();
            context.setVariable("email", to);
            context.setVariable("otp", otp);
            context.setVariable("expiryMinutes", 5);
            context.setVariable("verifyUrl", verifyUrl);

            String htmlContent = templateEngine.process("otp-password-reset-email", context);

            helper.setTo(to);
            helper.setSubject("Đặt lại mật khẩu - Mã OTP");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP password reset email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", to, e);
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.");
        }
    }
}
