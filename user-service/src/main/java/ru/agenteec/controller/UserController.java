package ru.agenteec.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.entity.User;
import ru.agenteec.entity.GameHistory;
import ru.agenteec.repository.UserRepository;
import ru.agenteec.repository.GameHistoryRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final GameHistoryRepository gameHistoryRepository;

    public UserController(UserRepository userRepository, GameHistoryRepository gameHistoryRepository) {
        this.userRepository = userRepository;
        this.gameHistoryRepository = gameHistoryRepository;
    }
    @GetMapping("/leaderboard")
    public ResponseEntity<List<User>> getLeaderboard(@RequestParam("category") String category) {
        List<User> topUsers = switch (category.toUpperCase()) {
            case "BULLET" -> userRepository.findTop5ByOrderByRatingBulletDesc();
            case "BLITZ" -> userRepository.findTop5ByOrderByRatingBlitzDesc();
            case "CLASSICAL" -> userRepository.findTop5ByOrderByRatingClassicalDesc();
            default -> userRepository.findTop5ByOrderByRatingRapidDesc();
        };
        return ResponseEntity.ok(topUsers);
    }
    @GetMapping("/{username}")
    public ResponseEntity<User> getUserProfile(@PathVariable("username") String username) {
        return userRepository.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{username}/games")
    public ResponseEntity<List<GameHistory>> getUserGames(@PathVariable("username") String username) {
        List<GameHistory> games = gameHistoryRepository.findByWhitePlayerOrBlackPlayer(username, username);
        return ResponseEntity.ok(games);
    }
}