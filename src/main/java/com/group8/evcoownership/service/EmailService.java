package com.group8.evcoownership.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
