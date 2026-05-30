package ru.agenteec.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.agenteec.service.GameService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/lobby")
@CrossOrigin(origins = "*")
public class LobbyController {

    private final GameService gameService;
    private final String userServiceUrl;
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public LobbyController(GameService gameService, @Value("${app.user-service.internal-url}") String userServiceUrl) {
        this.gameService = gameService;
        this.userServiceUrl = userServiceUrl;
    }

    @GetMapping("/challenges")
    public ResponseEntity<List<Map<String, Object>>> getChallenges() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> openGameIds = gameService.getOpenChallenges();

        if (openGameIds != null) {
            for (String gameId : openGameIds) {
                String creator = gameService.getPlayerColor(gameId, "white");
                if (creator == null) continue;

                String category = gameService.getGameCategory(gameId);

                Map<String, Object> challenge = new HashMap<>();
                challenge.put("roomId", gameId);
                challenge.put("player", creator);
                challenge.put("type", category);

                int rating = 1500;
                try {
                    String url = userServiceUrl + "/api/v1/users/" + creator;
                    Map<?, ?> uMap = restTemplate.getForObject(url, Map.class);
                    if (uMap != null) {
                        String ratingKey = "rating" + category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase();
                        Object val = uMap.get(ratingKey);
                        if (val instanceof Number) {
                            rating = ((Number) val).intValue();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка выгрузки рейтинга создателя для лобби: " + e.getMessage());
                }

                challenge.put("rating", rating);
                result.add(challenge);
            }
        }
        return ResponseEntity.ok(result);
    }
}