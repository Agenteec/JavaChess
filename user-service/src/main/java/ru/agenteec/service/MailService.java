package ru.agenteec.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String baseUrl;

    public MailService(JavaMailSender mailSender, @Value("${app.user-service.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationUrl = baseUrl + "/api/v1/auth/verify?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@agenteec-chess.ru");
            message.setTo(toEmail);
            message.setSubject("Подтверждение аккаунта в Java Chess");
            message.setText("Добро пожаловать в Java Chess!\n\n" +
                    "Пожалуйста, подтвердите вашу регистрацию, перейдя по ссылке:\n" +
                    verificationUrl + "\n\n" +
                    "Если вы не регистрировались на нашем портале, просто проигнорируйте это письмо.");

            mailSender.send(message);
            System.out.println(">>> Письмо верификации успешно отправлено на адрес: " + toEmail);

        } catch (Exception e) {
            System.err.println(">>> Не удалось отправить письмо на " + toEmail + ". Причина: " + e.getMessage());

            System.out.println("==========================================================================");
            System.out.println(">>> ССЫЛКА ДЛЯ РУЧНОЙ АКТИВАЦИИ АККАУНТА:");
            System.out.println(">>> " + verificationUrl);
            System.out.println("==========================================================================");
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = baseUrl + "/?reset=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@agenteec-chess.ru");
            message.setTo(toEmail);
            message.setSubject("Сброс пароля в Agenteec Chess");
            message.setText("Вы запросили сброс пароля.\n\n" +
                    "Чтобы задать новый пароль, перейдите по ссылке (действует 1 час):\n" +
                    resetUrl + "\n\n" +
                    "Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо — ваш пароль не изменится.");

            mailSender.send(message);
            System.out.println(">>> Письмо сброса пароля отправлено на адрес: " + toEmail);

        } catch (Exception e) {
            System.err.println(">>> Не удалось отправить письмо сброса на " + toEmail + ". Причина: " + e.getMessage());
            System.out.println("==========================================================================");
            System.out.println(">>> ССЫЛКА ДЛЯ РУЧНОГО СБРОСА ПАРОЛЯ:");
            System.out.println(">>> " + resetUrl);
            System.out.println("==========================================================================");
        }
    }
}