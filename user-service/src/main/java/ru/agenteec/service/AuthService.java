package ru.agenteec.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import ru.agenteec.dto.AuthResponse;
import ru.agenteec.dto.LoginRequest;
import ru.agenteec.dto.RegisterRequest;
import ru.agenteec.entity.User;
import ru.agenteec.repository.UserRepository;
import ru.agenteec.security.JwtService;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final MailService mailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService, MailService mailService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken!");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setVerified(false);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);

        user.setRatingBullet(1500);
        user.setRatingBlitz(1500);
        user.setRatingRapid(1500);
        user.setRatingClassical(1500);
        user.setRatingCorrespondence(1500);

        userRepository.save(user);

        mailService.sendVerificationEmail(user.getEmail(), token);

        return new AuthResponse(null, user.getUsername(), 1500);
    }

    public boolean verifyUser(String token) {
        return userRepository.findAll().stream()
                .filter(user -> token.equals(user.getVerificationToken()))
                .findFirst()
                .map(user -> {
                    user.setVerified(true);
                    user.setVerificationToken(null);
                    userRepository.save(user);
                    return true;
                }).orElse(false);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password!");
        }

        if (!user.isVerified()) {
            throw new RuntimeException("Пожалуйста, подтвердите вашу электронную почту перед входом!");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRatingRapid());
    }
}