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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // ← THÊM DÒNG NÀY
    @Value("${app.frontend.url}")
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
            helper.setSubject("Account Registration Verification - EV Co-ownership");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP registration email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: {}", to, e);
            throw new RuntimeException("Unable to send email. Please try again later.");
        }
    }

    // Dùng cho lời mời tham gia nhóm
    public void sendInvitationEmail(
            String to,
            String groupName,
            String inviterName,
            String otp,
            LocalDateTime expiresAt,
            BigDecimal suggestedPercentage // có thể null
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("inviterName", inviterName);
            context.setVariable("groupName", groupName);
            context.setVariable("otp", otp);
            context.setVariable("expiresAt", expiresAt);
            context.setVariable("suggestedPercentage", suggestedPercentage);
            context.setVariable("websiteUrl", frontendUrl);

            String htmlContent = templateEngine.process("invitation-email", context);

            helper.setTo(to);
            helper.setSubject("Group Invitation - EV Co-ownership: " + groupName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Invitation email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send invitation email to: {}", to, e);
            throw new RuntimeException("Unable to send email. Please try again later.");
        }
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
            helper.setSubject("Password Reset - OTP Code");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP password reset email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", to, e);
            throw new RuntimeException("Unable to send email. Please try again later.");
        }
    }
}
