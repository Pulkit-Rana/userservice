package com.syncnest.userservice.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailTemplate {

        private final JavaMailSender mailSender;

        public void sendOtp(String to, String otp) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Your OTP Code");
            message.setText("Your OTP is: " + otp + ". It will expire in 60 seconds.");
            mailSender.send(message);
        }

        public void sendPasswordResetCode(String to, String resetCode) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("SyncNest Password Reset Code");
            message.setText("Your password reset code is: " + resetCode + ". It will expire in 10 minutes.");
            mailSender.send(message);
        }
    }
