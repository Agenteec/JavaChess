package ru.agenteec.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.agenteec.dto.GameMessage;
import ru.agenteec.dto.GameResponse;
import ru.agenteec.service.GameService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameService gameService;
    private final String userServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();
    private final Map<String, Queue<WebSocketSession>> matchmakingQueues = new ConcurrentHashMap<>();

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public GameWebSocketHandler(GameService gameService, @Value("${app.user-service.internal-url}") String userServiceUrl) {
        this.gameService = gameService;
        this.userServiceUrl = userServiceUrl;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            GameMessage payload = objectMapper.readValue(message.getPayload(), GameMessage.class);
            String action = payload.getAction().toUpperCase();

            switch (action) {
                case "QUEUE":
                    session.getAttributes().put("username", payload.getUsername());
                    handleMatchmakingQueue(session, payload);
                    break;
                case "JOIN":
                    session.getAttributes().put("username", payload.getUsername());
                    handleJoin(session, payload);
                    break;
                case "MOVE":
                    handleMove(session, payload);
                    break;
                case "SURRENDER":
                    handleSurrender(session, payload);
                    break;
                case "TIMEOUT":
                    handleTimeout(session, payload);
                    break;
                case "DRAW_OFFER":
                    handleDrawOffer(session, payload);
                    break;
                case "DRAW_ACCEPT":
                    handleDrawAccept(session, payload);
                    break;
                case "DRAW_DECLINE":
                    handleDrawDecline(session, payload);
                    break;
                case "CHAT":
                    handleChat(payload);
                    break;
                default:
                    sendError(session, "Неизвестное действие: " + action);
            }
        } catch (Exception e) {
            sendError(session, "Ошибка: " + e.getMessage());
        }
    }

    private void handleMatchmakingQueue(WebSocketSession session, GameMessage payload) throws IOException {
        String category = payload.getCategory().toUpperCase();
        Queue<WebSocketSession> queue = matchmakingQueues.computeIfAbsent(category, k -> new ConcurrentLinkedQueue<>());

        session.getAttributes().put("minutes", payload.getMinutes() != null ? payload.getMinutes() : 10);
        session.getAttributes().put("increment", payload.getIncrement() != null ? payload.getIncrement() : 0);

        String username = (String) session.getAttributes().get("username");

        queue.removeIf(s -> username.equals(s.getAttributes().get("username")));
        queue.add(session);

        if (queue.size() >= 2) {
            WebSocketSession player1 = queue.poll();
            WebSocketSession player2 = queue.poll();

            if (player1 != null && player2 != null && player1.isOpen() && player2.isOpen()) {
                String gameUuid = UUID.randomUUID().toString();

                String u1 = (String) player1.getAttributes().get("username");
                String u2 = (String) player2.getAttributes().get("username");

                if (Math.random() > 0.5) {
                    gameService.assignPlayerColor(gameUuid, u1);
                } else {
                    gameService.assignPlayerColor(gameUuid, u2);
                }
                gameService.assignPlayerColor(gameUuid, u1.equals(gameService.getPlayerColor(gameUuid, "white")) ? u2 : u1);

                int mins = (int) player1.getAttributes().getOrDefault("minutes", 10);
                int inc = (int) player1.getAttributes().getOrDefault("increment", 0);
                gameService.initGameTime(gameUuid, mins, inc);
                gameService.setGameCategory(gameUuid, category);

                Map<String, String> redirectData = Map.of(
                        "type", "REDIRECT",
                        "url", "/?room=" + gameUuid
                );
                String json = objectMapper.writeValueAsString(redirectData);

                player1.sendMessage(new TextMessage(json));
                player2.sendMessage(new TextMessage(json));
            }
        }
    }

    private void handleJoin(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        String username = payload.getUsername();

        roomSessions.computeIfAbsent(gameId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        sessionRooms.put(session.getId(), gameId);

        if (username == null || username.isBlank()) {
            username = "Guest_" + Math.floor(1000 + Math.random() * 9000);
        }

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");
        boolean isArchived = false;
        String archiveResult = null;

        if (white == null && black == null) {
            try {
                String url = userServiceUrl + "/api/v1/internal/games/" + gameId;
                Map<?, ?> history = restTemplate.getForObject(url, Map.class);
                if (history != null) {
                    white = (String) history.get("whitePlayer");
                    black = (String) history.get("blackPlayer");
                    archiveResult = (String) history.get("result");
                    isArchived = true;
                }
            } catch (Exception e) {
                System.out.println("Партия не найдена в архиве PostgreSQL: " + e.getMessage());
            }
        }

        if (white == null && !isArchived) {
            gameService.assignPlayerColor(gameId, username);

            int mins = payload.getMinutes() != null ? payload.getMinutes() : 10;
            int inc = payload.getIncrement() != null ? payload.getIncrement() : 0;
            gameService.initGameTime(gameId, mins, inc);

            String category = "RAPID";
            if (mins < 3) category = "BULLET";
            else if (mins < 10) category = "BLITZ";
            else if (mins > 30) category = "CLASSICAL";

            gameService.setGameCategory(gameId, category);
            white = username;
        } else if (black == null && !username.equals(white) && !isArchived) {
            gameService.assignPlayerColor(gameId, username);
            black = username;

            String lastMoveKey = "chess:game:" + gameId + ":last_move_time";
            gameService.updateTimeOnMove(gameId, "WHITE");
        }

        if (gameService.getPlayerColor(gameId, "black") == null && !isArchived) {
            gameService.addOpenChallenge(gameId);
        } else {
            gameService.removeOpenChallenge(gameId);
        }

        String role = "SPECTATOR";
        if (username.equals(white)) role = "WHITE";
        else if (username.equals(black)) role = "BLACK";

        String fen = gameService.getOrCreateGame(gameId);
        Board board = gameService.getBoardState(gameId);

        long[] times = gameService.getGameTimes(gameId);

        boolean isMated = board.isMated() || isArchived;

        GameResponse response = new GameResponse(
                "STATE", fen, board.getSideToMove().toString(),
                isMated, board.isDraw() || (isArchived && "DRAW".equals(archiveResult)), times[0], times[1]
        );
        response.setLastMove(board.getHistory().isEmpty() ? null : board.getHistory().get(board.getHistory().size() - 1).toString().toLowerCase());

        if (isArchived) {
            response.setEndReason("MATE");
            if ("WHITE_WON".equals(archiveResult)) response.setLastMove("WHITE_WON");
            else if ("BLACK_WON".equals(archiveResult)) response.setLastMove("BLACK_WON");
            else response.setLastMove("DRAW");
        }

        response.setMoves(gameService.getGameMoves(gameId));
        response.setCategory(gameService.getGameCategory(gameId));

        int whiteRating = 1500;
        int blackRating = 1500;
        String gameCategory = gameService.getGameCategory(gameId);

        if (white != null && !white.startsWith("Guest")) {
            try {
                String url = userServiceUrl + "/api/v1/users/" + white;
                Map<?, ?> uMap = restTemplate.getForObject(url, Map.class);
                whiteRating = getCategoryRating(uMap, gameCategory);
            } catch (Exception e) {
                System.out.println("Не удалось получить рейтинг белых: " + e.getMessage());
            }
        }
        if (black != null && !black.startsWith("Guest")) {
            try {
                String url = userServiceUrl + "/api/v1/users/" + black;
                Map<?, ?> uMap = restTemplate.getForObject(url, Map.class);
                blackRating = getCategoryRating(uMap, gameCategory);
            } catch (Exception e) {
                System.out.println("Не удалось получить рейтинг черных: " + e.getMessage());
            }
        }

        String jsonResponse = objectMapper.writeValueAsString(response);
        Map<String, Object> baseMap = objectMapper.readValue(jsonResponse, Map.class);
        baseMap.put("whitePlayer", white != null ? white : "Ожидание соперника...");
        baseMap.put("blackPlayer", black != null ? black : "Ожидание соперника...");
        baseMap.put("whiteRating", whiteRating);
        baseMap.put("blackRating", blackRating);

        Map<String, Object> selfMap = new HashMap<>(baseMap);
        selfMap.put("role", isArchived ? "SPECTATOR" : role);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(selfMap)));

        Set<WebSocketSession> sessions = roomSessions.get(gameId);
        if (sessions != null) {
            String othersJson = objectMapper.writeValueAsString(baseMap);
            for (WebSocketSession s : sessions) {
                if (s != session && s.isOpen()) {
                    s.sendMessage(new TextMessage(othersJson));
                }
            }
        }
    }

    private void handleMove(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        String username = payload.getUsername();
        if (username == null || username.isBlank()) {
            username = (String) session.getAttributes().get("username");
        }

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        Board boardBefore = gameService.getBoardState(gameId);
        String currentTurn = boardBefore.getSideToMove().toString();

        boolean isWhiteTurn = currentTurn.equals("WHITE") && username.equals(white);
        boolean isBlackTurn = currentTurn.equals("BLACK") && username.equals(black);

        if (!isWhiteTurn && !isBlackTurn) {
            sendError(session, "Сейчас не ваш ход или вы зритель!");
            return;
        }

        try {
            List<String> movesList = gameService.makeMove(gameId, payload.getFrom(), payload.getTo());
            Board boardAfter = gameService.getBoardState(gameId);

            gameService.updateTimeOnMove(gameId, currentTurn);
            long[] times = gameService.getGameTimes(gameId);
            long whiteTime = times[0];
            long blackTime = times[1];

            boolean isTimeout = (whiteTime <= 0 || blackTime <= 0);

            GameResponse response = new GameResponse(
                    "STATE", boardAfter.getFen(), boardAfter.getSideToMove().toString(),
                    boardAfter.isMated() || isTimeout, boardAfter.isDraw(),
                    whiteTime, blackTime
            );
            response.setLastMove(payload.getFrom().toLowerCase() + "-" + payload.getTo().toLowerCase());

            List<String> lowercaseMoves = new ArrayList<>();
            for (String mStr : movesList) {
                lowercaseMoves.add(mStr.toLowerCase());
            }
            response.setMoves(lowercaseMoves);
            response.setCategory(gameService.getGameCategory(gameId));

            if (isTimeout) response.setLastMove("TIMEOUT");

            String endReason = null;
            if (boardAfter.isMated()) endReason = "MATE";
            else if (isTimeout) endReason = "TIMEOUT";
            else if (boardAfter.isDraw()) endReason = "DRAW";
            response.setEndReason(endReason);

            if (boardAfter.isMated() || boardAfter.isDraw() || isTimeout) {
                String result = isTimeout ? (whiteTime <= 0 ? "BLACK_WON" : "WHITE_WON") : (boardAfter.getSideToMove().toString().equals("BLACK") ? "WHITE_WON" : "BLACK_WON");

                Map<?, ?> ratingResult = sendGameResultToUserService(gameId, boardAfter, result);
                if (ratingResult != null) {
                    response.setWhiteRatingChange((Integer) ratingResult.get("whiteRatingChange"));
                    response.setBlackRatingChange((Integer) ratingResult.get("blackRatingChange"));
                    response.setWhiteNewRating((Integer) ratingResult.get("whiteNewRating"));
                    response.setBlackNewRating((Integer) ratingResult.get("blackNewRating"));
                }
            }

            broadcastToRoom(gameId, response);

        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleDrawOffer(WebSocketSession session, GameMessage payload) throws IOException {
        Map<String, String> drawOffer = Map.of(
                "type", "DRAW_OFFER_RECEIVED",
                "sender", payload.getUsername()
        );
        broadcastToRoom(payload.getGameId(), new GameResponse("DRAW_OFFER", payload.getUsername(), "Предложена ничья"));

        Set<WebSocketSession> sessions = roomSessions.get(payload.getGameId());
        if (sessions != null) {
            String json = objectMapper.writeValueAsString(drawOffer);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        }
    }

    private void handleDrawAccept(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        Board board = gameService.getBoardState(gameId);

        GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", false, true, 0, 0);
        response.setEndReason("DRAW");
        response.setLastMove("DRAW");
        response.setMoves(gameService.getGameMoves(gameId));
        response.setCategory(gameService.getGameCategory(gameId));

        Map<?, ?> ratingResult = sendGameResultToUserService(gameId, board, "DRAW");
        if (ratingResult != null) {
            response.setWhiteRatingChange((Integer) ratingResult.get("whiteRatingChange"));
            response.setBlackRatingChange((Integer) ratingResult.get("blackRatingChange"));
            response.setWhiteNewRating((Integer) ratingResult.get("whiteNewRating"));
            response.setBlackNewRating((Integer) ratingResult.get("blackNewRating"));
        }

        broadcastToRoom(gameId, response);
    }

    private void handleDrawDecline(WebSocketSession session, GameMessage payload) throws IOException {
        Map<String, String> drawDecline = Map.of(
                "type", "DRAW_DECLINED",
                "sender", payload.getUsername()
        );
        Set<WebSocketSession> sessions = roomSessions.get(payload.getGameId());
        if (sessions != null) {
            String json = objectMapper.writeValueAsString(drawDecline);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        }
    }

    private void handleSurrender(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        String username = payload.getUsername();
        if (username == null) username = (String) session.getAttributes().get("username");

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        if (white == null || black == null) return;

        String result = username.equals(white) ? "BLACK_WON" : "WHITE_WON";

        Board board = gameService.getBoardState(gameId);

        Map<?, ?> ratingResult = sendGameResultToUserService(gameId, board, result);

        GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, 0, 0);
        response.setLastMove("TIMEOUT");
        response.setEndReason("SURRENDER");
        response.setMoves(gameService.getGameMoves(gameId));
        response.setCategory(gameService.getGameCategory(gameId));

        if (ratingResult != null) {
            response.setWhiteRatingChange((Integer) ratingResult.get("whiteRatingChange"));
            response.setBlackRatingChange((Integer) ratingResult.get("blackRatingChange"));
            response.setWhiteNewRating((Integer) ratingResult.get("whiteNewRating"));
            response.setBlackNewRating((Integer) ratingResult.get("blackNewRating"));
        }

        broadcastToRoom(gameId, response);
    }

    private void handleChat(GameMessage payload) throws IOException {
        GameResponse response = new GameResponse("CHAT", payload.getUsername(), payload.getMessage());
        broadcastToRoom(payload.getGameId(), response);
    }

    private void broadcastToRoom(String gameId, GameResponse response) throws IOException {
        Set<WebSocketSession> sessions = roomSessions.get(gameId);
        if (sessions != null) {
            String jsonResponse = objectMapper.writeValueAsString(response);
            TextMessage textMessage = new TextMessage(jsonResponse);
            sessions.removeIf(session -> !session.isOpen());
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    private void sendError(WebSocketSession session, String errorMsg) throws IOException {
        if (session.isOpen()) {
            GameResponse response = new GameResponse("ERROR", errorMsg);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gameId = sessionRooms.remove(session.getId());
        if (gameId != null) {
            Set<WebSocketSession> sessions = roomSessions.get(gameId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(gameId);
                }
            }
        }
    }

    private Map<?, ?> sendGameResultToUserService(String gameId, Board board, String result) {
        try {
            String white = gameService.getPlayerColor(gameId, "white");
            String black = gameService.getPlayerColor(gameId, "black");
            if (white == null || black == null) return null;

            String category = gameService.getGameCategory(gameId);

            Map<String, Object> request = new HashMap<>();
            request.put("gameId", gameId);
            request.put("whitePlayer", white);
            request.put("blackPlayer", black);
            request.put("result", result);
            request.put("category", category);
            request.put("pgn", board.getHistory().toString());

            String targetUrl = this.userServiceUrl + "/api/v1/internal/games/complete";
            var responseEntity = restTemplate.postForEntity(targetUrl, request, Map.class);
            System.out.println(">>> Игра " + gameId + " завершена со статусом " + result + ". Результаты отправлены!");
            return responseEntity.getBody();
        } catch (Exception e) {
            System.err.println("Ошибка отправки результатов игры: " + e.getMessage());
            return null;
        }
    }

    private int getCategoryRating(Map<?, ?> userMap, String category) {
        if (userMap == null) return 1500;
        String key = switch (category.toUpperCase()) {
            case "BULLET" -> "ratingBullet";
            case "BLITZ" -> "ratingBlitz";
            case "CLASSICAL" -> "ratingClassical";
            case "CORRESPONDENCE" -> "ratingCorrespondence";
            default -> "ratingRapid";
        };
        Object val = userMap.get(key);
        return val instanceof Number ? ((Number) val).intValue() : 1500;
    }

    private void handleTimeout(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();

        long[] times = gameService.getGameTimes(gameId);
        long whiteTime = times[0];
        long blackTime = times[1];

        if (whiteTime <= 0 || blackTime <= 0) {
            String white = gameService.getPlayerColor(gameId, "white");
            String black = gameService.getPlayerColor(gameId, "black");
            if (white == null || black == null) return;

            Board board = gameService.getBoardState(gameId);
            int movesCount = board.getHistory().size();

            if (movesCount < 2) {
                GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, 0, 0);
                response.setLastMove("ABORTED");
                response.setEndReason("ABORTED");
                response.setMoves(gameService.getGameMoves(gameId));
                response.setCategory(gameService.getGameCategory(gameId));

                sendGameResultToUserService(gameId, board, "ABORTED");
                broadcastToRoom(gameId, response);
            } else {
                String result = (whiteTime <= 0) ? "BLACK_WON" : "WHITE_WON";

                GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, whiteTime, blackTime);
                response.setLastMove("TIMEOUT");
                response.setEndReason("TIMEOUT");
                response.setMoves(gameService.getGameMoves(gameId));
                response.setCategory(gameService.getGameCategory(gameId));

                Map<?, ?> ratingResult = sendGameResultToUserService(gameId, board, result);
                if (ratingResult != null) {
                    response.setWhiteRatingChange((Integer) ratingResult.get("whiteRatingChange"));
                    response.setBlackRatingChange((Integer) ratingResult.get("blackRatingChange"));
                    response.setWhiteNewRating((Integer) ratingResult.get("whiteNewRating"));
                    response.setBlackNewRating((Integer) ratingResult.get("blackNewRating"));
                }
                broadcastToRoom(gameId, response);
            }
        }
    }
}