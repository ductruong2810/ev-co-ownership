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
