package ru.agenteec.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.dto.GameResultRequest;
import ru.agenteec.entity.GameHistory;
import ru.agenteec.entity.User;
import ru.agenteec.repository.GameHistoryRepository;
import ru.agenteec.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/games")
public class InternalGameController {

    private final GameHistoryRepository gameHistoryRepository;
    private final UserRepository userRepository;

    public InternalGameController(GameHistoryRepository gameHistoryRepository, UserRepository userRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userRepository = userRepository;
    }
    @GetMapping("/{gameId}")
    public ResponseEntity<GameHistory> getGameHistory(@PathVariable("gameId") String gameId) {
        return gameHistoryRepository.findByGameId(gameId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> completeGame(@RequestBody GameResultRequest request) {
        String category = request.getCategory() != null ? request.getCategory().toUpperCase() : "RAPID";

        GameHistory history = new GameHistory();
        history.setGameId(request.getGameId());
        history.setWhitePlayer(request.getWhitePlayer());
        history.setBlackPlayer(request.getBlackPlayer());
        history.setResult(request.getResult());
        history.setPgn(request.getPgn());
        history.setCategory(category);
        gameHistoryRepository.save(history);

        User white = userRepository.findByUsername(request.getWhitePlayer()).orElse(null);
        User black = userRepository.findByUsername(request.getBlackPlayer()).orElse(null);

        Map<String, Object> responseMap = new HashMap<>();

        if (white != null && black != null) {
            int oldRatingW = getRating(white, category);
            int oldRatingB = getRating(black, category);

            calculateAndApplyElo(white, black, request.getResult(), category);

            userRepository.save(white);
            userRepository.save(black);

            int newRatingW = getRating(white, category);
            int newRatingB = getRating(black, category);

            responseMap.put("whiteNewRating", newRatingW);
            responseMap.put("whiteRatingChange", newRatingW - oldRatingW);
            responseMap.put("blackNewRating", newRatingB);
            responseMap.put("blackRatingChange", newRatingB - oldRatingB);
        } else {
            responseMap.put("whiteNewRating", 1500);
            responseMap.put("whiteRatingChange", 0);
            responseMap.put("blackNewRating", 1500);
            responseMap.put("blackRatingChange", 0);
        }

        return ResponseEntity.ok(responseMap);
    }

    private void calculateAndApplyElo(User white, User black, String result, String category) {
        int ratingW = getRating(white, category);
        int ratingB = getRating(black, category);
        int gamesW = getGamesCount(white, category);
        int gamesB = getGamesCount(black, category);

        int kW = gamesW < 10 ? 40 : 15;
        int kB = gamesB < 10 ? 40 : 15;

        double scoreW = result.equals("WHITE_WON") ? 1.0 : (result.equals("DRAW") ? 0.5 : 0.0);
        double scoreB = 1.0 - scoreW;

        double expectedW = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingW) / 400.0));
        double expectedB = 1.0 / (1.0 + Math.pow(10.0, (ratingW - ratingB) / 400.0));

        setRating(white, category, (int) Math.round(ratingW + kW * (scoreW - expectedW)));
        setRating(black, category, (int) Math.round(ratingB + kB * (scoreB - expectedB)));

        incrementGamesCount(white, category);
        incrementGamesCount(black, category);
    }

    private int getRating(User user, String category) {
        return switch (category) {
            case "BULLET" -> user.getRatingBullet();
            case "BLITZ" -> user.getRatingBlitz();
            case "CLASSICAL" -> user.getRatingClassical();
            case "CORRESPONDENCE" -> user.getRatingCorrespondence();
            default -> user.getRatingRapid();
        };
    }

    private void setRating(User user, String category, int value) {
        switch (category) {
            case "BULLET" -> user.setRatingBullet(value);
            case "BLITZ" -> user.setRatingBlitz(value);
            case "CLASSICAL" -> user.setRatingClassical(value);
            case "CORRESPONDENCE" -> user.setRatingCorrespondence(value);
            default -> user.setRatingRapid(value);
        }
    }

    private int getGamesCount(User user, String category) {
        return switch (category) {
            case "BULLET" -> user.getGamesBullet();
            case "BLITZ" -> user.getGamesBlitz();
            case "CLASSICAL" -> user.getGamesClassical();
            case "CORRESPONDENCE" -> user.getGamesCorrespondence();
            default -> user.getGamesRapid();
        };
    }

    private void incrementGamesCount(User user, String category) {
        switch (category) {
            case "BULLET" -> user.setGamesBullet(user.getGamesBullet() + 1);
            case "BLITZ" -> user.setGamesBlitz(user.getGamesBlitz() + 1);
            case "CLASSICAL" -> user.setGamesClassical(user.getGamesClassical() + 1);
            case "CORRESPONDENCE" -> user.setGamesCorrespondence(user.getGamesCorrespondence() + 1);
            default -> user.setGamesRapid(user.getGamesRapid() + 1);
        }
    }
}