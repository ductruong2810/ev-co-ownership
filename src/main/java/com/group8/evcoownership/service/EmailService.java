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
}
