package ru.agenteec.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.dto.GameResultRequest;
import ru.agenteec.entity.GameHistory;
import ru.agenteec.entity.User;
import ru.agenteec.repository.GameHistoryRepository;
import ru.agenteec.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/internal/games")
public class InternalGameController {

    private final GameHistoryRepository gameHistoryRepository;
    private final UserRepository userRepository;

    public InternalGameController(GameHistoryRepository gameHistoryRepository, UserRepository userRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/complete")
    public ResponseEntity<String> completeGame(@RequestBody GameResultRequest request) {
        GameHistory history = new GameHistory();
        history.setGameId(request.getGameId());
        history.setWhitePlayer(request.getWhitePlayer());
        history.setBlackPlayer(request.getBlackPlayer());
        history.setResult(request.getResult());
        history.setPgn(request.getPgn());
        gameHistoryRepository.save(history);

        User white = userRepository.findByUsername(request.getWhitePlayer()).orElse(null);
        User black = userRepository.findByUsername(request.getBlackPlayer()).orElse(null);

        if (white != null && black != null) {
            calculateAndApplyElo(white, black, request.getResult());
            userRepository.save(white);
            userRepository.save(black);
        }

        return ResponseEntity.ok("Game result saved, elo updated");
    }

    private void calculateAndApplyElo(User white, User black, String result) {
        int kFactor = 30;

        double scoreWhite = result.equals("WHITE_WON") ? 1.0 : (result.equals("DRAW") ? 0.5 : 0.0);
        double scoreBlack = 1.0 - scoreWhite;

        double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (black.getRating() - white.getRating()) / 400.0));
        double expectedBlack = 1.0 / (1.0 + Math.pow(10.0, (white.getRating() - black.getRating()) / 400.0));

        white.setRating((int) Math.round(white.getRating() + kFactor * (scoreWhite - expectedWhite)));
        black.setRating((int) Math.round(black.getRating() + kFactor * (scoreBlack - expectedBlack)));
    }
}