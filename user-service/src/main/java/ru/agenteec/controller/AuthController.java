package ru.agenteec.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.dto.AuthResponse;
import ru.agenteec.dto.LoginRequest;
import ru.agenteec.dto.RegisterRequest;
import ru.agenteec.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/resend")
    public ResponseEntity<String> resendVerification(@RequestParam("email") String email) {
        try {
            authService.resendVerification(email);
            return ResponseEntity.ok("Ссылка успешно отправлена повторно!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    @GetMapping("/verify")
    public ResponseEntity<String> verifyUser(@RequestParam("token") String token) {
        boolean isVerified = authService.verifyUser(token);
        if (isVerified) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body("<html><body style='font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #161512; color: #fff;'>" +
                            "<h1 style='color: #577d36;'>✔ Аккаунт успешно активирован!</h1>" +
                            "<p>Поздравляем! Теперь вы можете вернуться на главную страницу, войти в свой аккаунт и начать играть.</p>" +
                            "<a href='http://localhost:8082/auth.html' style='color: #ffcc00; font-weight: bold; text-decoration: none;'>Перейти к авторизации →</a>" +
                            "</body></html>");
        } else {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body("<html><body style='font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #161512; color: #fff;'>" +
                            "<h1 style='color: #dc3545;'> Ошибка активации</h1>" +
                            "<p>Токен неверен, устарел или уже был использован.</p>" +
                            "</body></html>");
        }
    }
    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> registerGuest() {
        return ResponseEntity.ok(authService.generateGuestToken());
    }
}