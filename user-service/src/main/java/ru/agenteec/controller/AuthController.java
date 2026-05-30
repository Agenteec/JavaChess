package ru.agenteec.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.dto.AuthResponse;
import ru.agenteec.dto.LoginRequest;
import ru.agenteec.dto.RegisterRequest;
import ru.agenteec.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/resend")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> body) {
        String login = body.getOrDefault("login", body.get("email"));
        authService.resendVerification(login);
        return ResponseEntity.ok(Map.of("message", "Письмо с подтверждением отправлено повторно."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        authService.requestPasswordReset(body.get("email"));
        return ResponseEntity.ok(Map.of("message",
                "Если аккаунт с такой почтой существует, мы отправили на неё ссылку для сброса пароля."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Пароль успешно изменён. Теперь можно войти."));
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
                    .body(renderResultPage(true, "Аккаунт активирован!",
                            "Поздравляем! Теперь войдите в свой аккаунт и начинайте играть."));
        } else {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(renderResultPage(false, "Не удалось активировать",
                            "Ссылка неверна, устарела или уже была использована. Запросите письмо повторно на странице входа."));
        }
    }

    private String renderResultPage(boolean success, String title, String message) {
        String accent = success ? "#6E9F4A" : "#dc3545";
        String tint = success ? "rgba(110,159,74,0.15)" : "rgba(220,53,69,0.15)";
        String icon = success
                ? "<path d='M20 6 9 17l-5-5'/>"
                : "<path d='M18 6 6 18'/><path d='M6 6l12 12'/>";

        String html = """
                <!doctype html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Agenteec Chess</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com"/>
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin/>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin:0; font-family:'Inter',system-ui,sans-serif; background:#15130F; color:#E9E5DD;
                           min-height:100vh; display:flex; align-items:center; justify-content:center; padding:24px; }
                    .card { background:#201D17; border:1px solid #322C24; border-radius:18px; padding:40px 32px;
                            max-width:440px; width:100%; text-align:center; box-shadow:0 24px 60px rgba(0,0,0,.5); animation:rise .35s ease-out; }
                    @keyframes rise { from { opacity:0; transform:translateY(10px); } to { opacity:1; transform:none; } }
                    .brand { font-weight:800; font-size:14px; color:#9A958C; letter-spacing:.04em; margin-bottom:24px; }
                    .brand b { color:#E8C56A; }
                    .icon { width:80px; height:80px; border-radius:50%; background:{TINT}; display:flex;
                            align-items:center; justify-content:center; margin:0 auto 22px; }
                    h1 { font-size:24px; font-weight:700; margin:0 0 10px; color:#fff; }
                    p { font-size:14px; line-height:1.6; color:#9A958C; margin:0 0 28px; }
                    .btn { display:inline-block; background:#6E9F4A; color:#fff; font-weight:700; font-size:15px;
                           text-decoration:none; padding:13px 28px; border-radius:10px; transition:background .15s; }
                    .btn:hover { background:#7DB356; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="brand">&#9822; <b>Agenteec Chess</b></div>
                    <div class="icon">
                      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="{ACCENT}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">{ICON}</svg>
                    </div>
                    <h1>{TITLE}</h1>
                    <p>{MESSAGE}</p>
                    <a class="btn" href="/">Перейти на сайт &#8594;</a>
                  </div>
                </body>
                </html>
                """;

        return html.replace("{TINT}", tint)
                .replace("{ACCENT}", accent)
                .replace("{ICON}", icon)
                .replace("{TITLE}", title)
                .replace("{MESSAGE}", message);
    }
    @PostMapping("/guest")
    public ResponseEntity<AuthResponse> registerGuest() {
        return ResponseEntity.ok(authService.generateGuestToken());
    }
}