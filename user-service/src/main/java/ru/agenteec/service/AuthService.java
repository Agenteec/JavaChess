package ru.agenteec.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import ru.agenteec.dto.AuthResponse;
import ru.agenteec.dto.LoginRequest;
import ru.agenteec.dto.RegisterRequest;
import ru.agenteec.entity.User;
import ru.agenteec.repository.UserRepository;
import ru.agenteec.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken!");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRatingBullet(1500);
        user.setRatingBlitz(1500);
        user.setRatingRapid(1500);
        user.setRatingClassical(1500);
        user.setRatingCorrespondence(1500);

        user.setGamesBullet(0);
        user.setGamesBlitz(0);
        user.setGamesRapid(0);
        user.setGamesClassical(0);
        user.setGamesCorrespondence(0);

        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRatingRapid());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password!");
        }

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRatingRapid());
    }
}