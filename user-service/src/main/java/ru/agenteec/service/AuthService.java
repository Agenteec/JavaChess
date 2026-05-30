package ru.agenteec.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import ru.agenteec.dto.AuthResponse;
import ru.agenteec.dto.LoginRequest;
import ru.agenteec.dto.RegisterRequest;
import ru.agenteec.entity.User;
import ru.agenteec.exception.AuthException;
import ru.agenteec.repository.UserRepository;
import ru.agenteec.security.JwtService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final MailService mailService;
    private final CaptchaService captchaService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final Pattern EMAIL_RE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final long VERIFICATION_TTL_HOURS = 24;
    private static final long RESET_TTL_HOURS = 1;

    public AuthService(UserRepository userRepository, JwtService jwtService, MailService mailService, CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.captchaService = captchaService;
    }

    public AuthResponse generateGuestToken() {
        String guestId = "anon-" + UUID.randomUUID().toString();
        String guestName = "Anonymous";
        String token = jwtService.generateToken(guestId, guestName, "ROLE_GUEST");
        return new AuthResponse(token, guestName, 1500);
    }

    public AuthResponse register(RegisterRequest request) {
        if (!captchaService.verify(request.getCaptchaToken())) {
            throw new AuthException("Подтвердите, что вы не робот, и попробуйте снова.");
        }

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String password = request.getPassword() == null ? "" : request.getPassword();

        if (username.length() < 3 || username.length() > 20) {
            throw new AuthException("Имя пользователя должно быть от 3 до 20 символов.");
        }
        if (!username.matches("^[A-Za-z0-9_-]+$")) {
            throw new AuthException("Имя может содержать только латиницу, цифры, _ и -.");
        }
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new AuthException("Укажите корректный адрес электронной почты.");
        }
        if (password.length() < 6) {
            throw new AuthException("Пароль должен быть не короче 6 символов.");
        }

        Optional<User> byUsername = userRepository.findByUsername(username);
        Optional<User> byEmail = userRepository.findByEmail(email);

        if (byUsername.isPresent() && byUsername.get().isVerified()) {
            throw new AuthException("Имя пользователя уже занято.");
        }
        if (byEmail.isPresent() && byEmail.get().isVerified()) {
            throw new AuthException("Эта почта уже используется.");
        }
        if (byUsername.isPresent()) {
            userRepository.delete(byUsername.get());
        }
        if (byEmail.isPresent() && (byUsername.isEmpty() || !byEmail.get().getId().equals(byUsername.get().getId()))) {
            userRepository.delete(byEmail.get());
        }
        userRepository.flush();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setVerified(false);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TTL_HOURS));

        userRepository.save(user);
        mailService.sendVerificationEmail(user.getEmail(), token);

        return new AuthResponse(null, user.getUsername(), 1500);
    }

    public boolean verifyUser(String token) {
        return userRepository.findByVerificationToken(token)
                .filter(user -> user.getVerificationTokenExpiry() == null
                        || user.getVerificationTokenExpiry().isAfter(LocalDateTime.now()))
                .map(user -> {
                    user.setVerified(true);
                    user.setVerificationToken(null);
                    user.setVerificationTokenExpiry(null);
                    userRepository.save(user);
                    return true;
                }).orElse(false);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername() == null ? "" : request.getUsername().trim())
                .orElseThrow(() -> new AuthException("Неверный логин или пароль."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException("Неверный логин или пароль.");
        }

        if (!user.isVerified()) {
            throw new AuthException("Пожалуйста, подтвердите вашу электронную почту перед входом!");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getUsername(), "ROLE_USER");
        return new AuthResponse(token, user.getUsername(), user.getRatingRapid());
    }

    public void resendVerification(String usernameOrEmail) {
        String value = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        User user = userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmail(value.toLowerCase()))
                .orElseThrow(() -> new AuthException("Пользователь не найден."));

        if (user.isVerified()) {
            throw new AuthException("Аккаунт уже подтверждён — можно входить.");
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TTL_HOURS));
        userRepository.save(user);

        mailService.sendVerificationEmail(user.getEmail(), newToken);
    }

    public void requestPasswordReset(String email) {
        String value = email == null ? "" : email.trim().toLowerCase();
        userRepository.findByEmail(value).ifPresent(user -> {
            if (!user.isVerified()) return;
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(RESET_TTL_HOURS));
            userRepository.save(user);
            mailService.sendPasswordResetEmail(user.getEmail(), token);
        });
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new AuthException("Ссылка недействительна.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new AuthException("Пароль должен быть не короче 6 символов.");
        }

        User user = userRepository.findByResetToken(token)
                .filter(u -> u.getResetTokenExpiry() != null && u.getResetTokenExpiry().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new AuthException("Ссылка для сброса недействительна или устарела. Запросите новую."));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }
}
