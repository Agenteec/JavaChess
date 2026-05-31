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
import ru.agenteec.security.JwtService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final JwtService jwtService;
    private final GameService gameService;
    private final String userServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();
    private final Map<String, Queue<WebSocketSession>> matchmakingQueues = new ConcurrentHashMap<>();

    private final org.springframework.web.client.RestTemplate restTemplate;

    public GameWebSocketHandler(GameService gameService,
                                @Value("${app.user-service.internal-url}") String userServiceUrl,
                                JwtService jwtService,
                                org.springframework.web.client.RestTemplate restTemplate) {
        this.gameService = gameService;
        this.userServiceUrl = userServiceUrl;
        this.jwtService = jwtService;
        this.restTemplate = restTemplate;
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
                    handleChat(session, payload);
                    break;
                case "PING":
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                    }
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
        String requestedUsername = payload.getUsername();
        String token = payload.getToken();

        String validatedUsername = null;
        String role = null;
        if (token != null && !token.isBlank()) {
            validatedUsername = jwtService.extractUsername(token);
            role = jwtService.extractRole(token);
        }

        if (validatedUsername == null) {
            if (requestedUsername != null && requestedUsername.startsWith("Guest_")) {
                validatedUsername = requestedUsername;
                role = "ROLE_GUEST";
            } else {
                sendError(session, "Ошибка авторизации: требуется войти в систему.");
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        }

        session.getAttributes().put("username", validatedUsername);
        session.getAttributes().put("role", role);

        String roleFromSession = (String) session.getAttributes().get("role");
        String queueKey = "ROLE_GUEST".equals(roleFromSession) ? category + "_GUEST" : category;

        Queue<WebSocketSession> queue = matchmakingQueues.computeIfAbsent(queueKey, k -> new ConcurrentLinkedQueue<>());

        session.getAttributes().put("minutes", payload.getMinutes() != null ? payload.getMinutes() : 10);
        session.getAttributes().put("increment", payload.getIncrement() != null ? payload.getIncrement() : 0);

        String username = validatedUsername;
        queue.removeIf(s -> !s.isOpen());
        queue.removeIf(s -> username.equals(s.getAttributes().get("username")));
        queue.add(session);

        while (queue.size() >= 2) {
            WebSocketSession player1 = queue.poll();
            if (player1 == null) break;
            if (!player1.isOpen()) continue;

            WebSocketSession player2 = queue.poll();
            if (player2 == null) { queue.add(player1); break; }
            if (!player2.isOpen()) { queue.add(player1); continue; }

            String gameUuid = UUID.randomUUID().toString();

            String u1 = (String) player1.getAttributes().get("username");
            String u2 = (String) player2.getAttributes().get("username");

            String whiteUser = Math.random() > 0.5 ? u1 : u2;
            String blackUser = whiteUser.equals(u1) ? u2 : u1;
            gameService.assignPlayerColor(gameUuid, whiteUser);
            gameService.assignPlayerColor(gameUuid, blackUser);

            int mins = (int) player1.getAttributes().getOrDefault("minutes", 10);
            int inc = (int) player1.getAttributes().getOrDefault("increment", 0);
            gameService.initGameTime(gameUuid, mins, inc);
            gameService.setGameCategory(gameUuid, category);

            Map<String, String> redirectData = Map.of(
                    "type", "REDIRECT",
                    "url", "/?room=" + gameUuid + "&mins=" + mins + "&inc=" + inc
            );
            String json = objectMapper.writeValueAsString(redirectData);

            player1.sendMessage(new TextMessage(json));
            player2.sendMessage(new TextMessage(json));
            break;
        }
    }


    private void handleJoin(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        String token = payload.getToken();

        if (token == null || token.isBlank()) {
            sendError(session, "Ошибка доступа: отсутствует токен авторизации.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String userId = jwtService.extractUsername(token);
        String displayName = jwtService.extractDisplayName(token);
        String role = jwtService.extractRole(token);

        if (userId == null || role == null) {
            sendError(session, "Ошибка доступа: недействительный токен.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put("userId", userId);
        session.getAttributes().put("username", displayName);
        session.getAttributes().put("role", role);

        roomSessions.computeIfAbsent(gameId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        sessionRooms.put(session.getId(), gameId);

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");
        boolean isArchived = false;
        String archiveResult = null;
        Integer archWhiteRating = null, archBlackRating = null, archWhiteChange = null, archBlackChange = null;

        if (white == null && black == null) {
            try {
                String url = userServiceUrl + "/api/v1/internal/games/" + gameId;
                Map<?, ?> history = restTemplate.getForObject(url, Map.class);
                if (history != null) {
                    white = (String) history.get("whitePlayer");
                    black = (String) history.get("blackPlayer");
                    archiveResult = (String) history.get("result");
                    archWhiteRating = toInt(history.get("whiteRating"));
                    archBlackRating = toInt(history.get("blackRating"));
                    archWhiteChange = toInt(history.get("whiteRatingChange"));
                    archBlackChange = toInt(history.get("blackRatingChange"));
                    isArchived = true;
                }
            } catch (Exception e) {
                System.out.println("Партия не найдена в архиве PostgreSQL: " + e.getMessage());
            }
        }

        boolean lobbyVisible = false;
        if (!isArchived) {
            if (white == null && black == null) {
                int mins = payload.getMinutes() != null ? payload.getMinutes() : 10;
                int inc = payload.getIncrement() != null ? payload.getIncrement() : 0;
                gameService.initGameTime(gameId, mins, inc);

                String category = "RAPID";
                if (mins < 3) category = "BULLET";
                else if (mins < 10) category = "BLITZ";
                else if (mins > 30) category = "CLASSICAL";
                gameService.setGameCategory(gameId, category);

                gameService.setVariant(gameId, payload.getVariant());

                boolean rated = !Boolean.FALSE.equals(payload.getRated());
                if (userId.startsWith("anon-")) rated = false;
                gameService.setRated(gameId, rated);

                String colorChoice = payload.getColor() != null ? payload.getColor().toUpperCase() : "WHITE";
                if (rated || "RANDOM".equals(colorChoice)) {
                    colorChoice = Math.random() < 0.5 ? "WHITE" : "BLACK";
                }
                if ("BLACK".equals(colorChoice)) {
                    gameService.setColorAssignment(gameId, "black", userId);
                    black = userId;
                } else {
                    gameService.setColorAssignment(gameId, "white", userId);
                    white = userId;
                }
                lobbyVisible = rated || Boolean.TRUE.equals(payload.getLobby());
                gameService.setLobbyVisible(gameId, lobbyVisible);
            } else if (white == null && !userId.equals(black)) {
                gameService.setColorAssignment(gameId, "white", userId);
                white = userId;
                gameService.updateTimeOnMove(gameId, "WHITE");
            } else if (black == null && !userId.equals(white)) {
                gameService.setColorAssignment(gameId, "black", userId);
                black = userId;
                gameService.updateTimeOnMove(gameId, "WHITE");
            }
        }

        boolean stillWaiting = !isArchived && (gameService.getPlayerColor(gameId, "white") == null
                || gameService.getPlayerColor(gameId, "black") == null);
        if (stillWaiting) {
            if (gameService.isLobbyVisible(gameId)) {
                gameService.addOpenChallenge(gameId);
            }
        } else {
            gameService.removeOpenChallenge(gameId);
        }

        String userRole = "SPECTATOR";
        if (userId.equals(white)) userRole = "WHITE";
        else if (userId.equals(black)) userRole = "BLACK";

        String fen = gameService.getOrCreateGame(gameId);
        Board board = gameService.getBoardState(gameId);
        long[] times = gameService.getGameTimes(gameId);

        String[] stored = gameService.getGameResult(gameId);
        String finalResult = isArchived ? archiveResult : (stored != null ? stored[0] : null);
        String finalReason = isArchived ? null : (stored != null ? stored[1] : null);
        boolean finished = finalResult != null;

        long wTime = times[0], bTime = times[1];
        if (!isArchived && stored != null && stored[2] != null && stored[3] != null) {
            try { wTime = Long.parseLong(stored[2]); bTime = Long.parseLong(stored[3]); } catch (Exception ignored) {}
        }

        boolean isMated = board.isMated() || finished;

        GameResponse response = new GameResponse(
                "STATE", fen, board.getSideToMove().toString(),
                isMated, board.isDraw() || "DRAW".equals(finalResult), wTime, bTime
        );
        response.setLastMove(board.getHistory().isEmpty() ? null : board.getHistory().get(board.getHistory().size() - 1).toString().toLowerCase());

        if (finished) {
            if (finalReason == null) finalReason = "DRAW".equals(finalResult) ? "DRAW" : "MATE";
            response.setEndReason(finalReason);
            response.setResult(finalResult);
        }

        response.setMoves(gameService.getGameMoves(gameId));
        response.setCategory(gameService.getGameCategory(gameId));

        int whiteRating = 1500;
        int blackRating = 1500;
        String gameCategory = gameService.getGameCategory(gameId);

        if (white != null && !white.startsWith("anon-")) {
            try {
                String url = userServiceUrl + "/api/v1/users/" + white;
                Map<?, ?> uMap = restTemplate.getForObject(url, Map.class);
                whiteRating = getCategoryRating(uMap, gameCategory);
            } catch (Exception e) {
                System.out.println("Не удалось получить рейтинг белых: " + e.getMessage());
            }
        }
        if (black != null && !black.startsWith("anon-")) {
            try {
                String url = userServiceUrl + "/api/v1/users/" + black;
                Map<?, ?> uMap = restTemplate.getForObject(url, Map.class);
                blackRating = getCategoryRating(uMap, gameCategory);
            } catch (Exception e) {
                System.out.println("Не удалось получить рейтинг черных: " + e.getMessage());
            }
        }

        if (isArchived) {
            if (archWhiteRating != null) whiteRating = archWhiteRating;
            if (archBlackRating != null) blackRating = archBlackRating;
            response.setWhiteNewRating(archWhiteRating);
            response.setBlackNewRating(archBlackRating);
            response.setWhiteRatingChange(archWhiteChange);
            response.setBlackRatingChange(archBlackChange);
        }

        String whiteNameFormatted = (white == null) ? "Ожидание..." : (white.startsWith("anon-") ? "Anonymous" : white);
        String blackNameFormatted = (black == null) ? "Ожидание..." : (black.startsWith("anon-") ? "Anonymous" : black);

        String jsonResponse = objectMapper.writeValueAsString(response);
        Map<String, Object> baseMap = objectMapper.readValue(jsonResponse, Map.class);
        baseMap.put("whitePlayer", whiteNameFormatted);
        baseMap.put("blackPlayer", blackNameFormatted);
        baseMap.put("whiteRating", whiteRating);
        baseMap.put("blackRating", blackRating);
        baseMap.put("rated", gameService.isRated(gameId));
        baseMap.put("variant", gameService.getVariant(gameId));

        Map<String, Object> selfMap = new HashMap<>(baseMap);
        selfMap.put("role", isArchived ? "SPECTATOR" : userRole);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(selfMap)));

        Map<String, Object> chatHistory = new HashMap<>();
        chatHistory.put("type", "CHAT_HISTORY");
        chatHistory.put("messages", gameService.getChatMessages(gameId));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatHistory)));

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
        String userId = (String) session.getAttributes().get("userId");

        if (gameService.isGameFinished(gameId)) {
            sendError(session, "Партия уже завершена.");
            return;
        }

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        Board boardBefore = gameService.getBoardState(gameId);
        String currentTurn = boardBefore.getSideToMove().toString();

        boolean isWhiteTurn = currentTurn.equals("WHITE") && userId.equals(white);
        boolean isBlackTurn = currentTurn.equals("BLACK") && userId.equals(black);

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
                if (boardAfter.isDraw()) result = "DRAW";

                response.setResult(result);
                gameService.setGameResult(gameId, result, endReason, whiteTime, blackTime);

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
        String gameId = payload.getGameId();
        String sender = (String) session.getAttributes().get("username");
        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        GameResponse offer = new GameResponse("DRAW_OFFER_RECEIVED", sender, "Предложена ничья");
        String json = objectMapper.writeValueAsString(offer);

        Set<WebSocketSession> sessions = roomSessions.get(gameId);
        if (sessions != null) {
            for (WebSocketSession s : sessions) {
                if (s == session || !s.isOpen()) continue;
                String uid = (String) s.getAttributes().get("userId");
                if (uid != null && (uid.equals(white) || uid.equals(black))) {
                    s.sendMessage(new TextMessage(json));
                }
            }
        }
    }

    private void handleDrawAccept(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();

        String userId = (String) session.getAttributes().get("userId");
        String w = gameService.getPlayerColor(gameId, "white");
        String b = gameService.getPlayerColor(gameId, "black");
        if (userId == null || (!userId.equals(w) && !userId.equals(b))) return;

        if (gameService.isGameFinished(gameId)) return;

        Board board = gameService.getBoardState(gameId);
        long[] times = gameService.getGameTimes(gameId);

        gameService.setGameResult(gameId, "DRAW", "DRAW", times[0], times[1]);

        GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", false, true, times[0], times[1]);
        response.setEndReason("DRAW");
        response.setResult("DRAW");
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
        String userId = (String) session.getAttributes().get("userId");

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        if (white == null || black == null) return;

        if (userId == null || (!userId.equals(white) && !userId.equals(black))) {
            sendError(session, "Только участник партии может сдаться.");
            return;
        }

        if (gameService.isGameFinished(gameId)) return;

        String result = userId.equals(white) ? "BLACK_WON" : "WHITE_WON";
        String winnerColor = "WHITE_WON".equals(result) ? "WHITE" : "BLACK";

        Board board = gameService.getBoardState(gameId);
        long[] times = gameService.getGameTimes(gameId);

        gameService.setGameResult(gameId, result, "SURRENDER", times[0], times[1]);
        Map<?, ?> ratingResult = sendGameResultToUserService(gameId, board, result);

        GameResponse response = new GameResponse("STATE", board.getFen(), winnerColor, true, false, times[0], times[1]);
        response.setEndReason("SURRENDER");
        response.setResult(result);
        response.setLastMove(board.getHistory().isEmpty() ? null : board.getHistory().get(board.getHistory().size() - 1).toString().toLowerCase());
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

    private void handleChat(WebSocketSession session, GameMessage payload) throws IOException {
        String username = (String) session.getAttributes().get("username");
        gameService.addChatMessage(payload.getGameId(), username, payload.getMessage());
        GameResponse response = new GameResponse("CHAT", username, payload.getMessage());
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

        for (Queue<WebSocketSession> queue : matchmakingQueues.values()) {
            queue.remove(session);
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
            request.put("whitePlayer", white.startsWith("anon-") ? "Anonymous" : white);
            request.put("blackPlayer", black.startsWith("anon-") ? "Anonymous" : black);
            request.put("result", result);
            request.put("category", category);
            request.put("pgn", board.getHistory().toString());
            request.put("rated", gameService.isRated(gameId));

            String targetUrl = this.userServiceUrl + "/api/v1/internal/games/complete";
            var responseEntity = restTemplate.postForEntity(targetUrl, request, Map.class);
            return responseEntity.getBody();
        } catch (Exception e) {
            System.err.println("Ошибка отправки результатов игры: " + e.getMessage());
            return null;
        }
    }

    private Integer toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : null;
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

        if (gameService.isGameFinished(gameId)) return;

        long[] times = gameService.getGameTimes(gameId);
        long whiteTime = times[0];
        long blackTime = times[1];

        if (whiteTime <= 0 || blackTime <= 0) {
            String white = gameService.getPlayerColor(gameId, "white");
            String black = gameService.getPlayerColor(gameId, "black");
            if (white == null || black == null) return;

            Board board = gameService.getBoardState(gameId);
            int movesCount = gameService.getGameMoves(gameId).size();

            if (movesCount < 2) {
                gameService.setGameResult(gameId, "ABORTED", "ABORTED", 0, 0);

                GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, 0, 0);
                response.setLastMove("ABORTED");
                response.setEndReason("ABORTED");
                response.setResult("ABORTED");
                response.setMoves(gameService.getGameMoves(gameId));
                response.setCategory(gameService.getGameCategory(gameId));

                sendGameResultToUserService(gameId, board, "ABORTED");
                broadcastToRoom(gameId, response);
            } else {
                String result = (whiteTime <= 0) ? "BLACK_WON" : "WHITE_WON";

                gameService.setGameResult(gameId, result, "TIMEOUT", whiteTime, blackTime);

                GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, whiteTime, blackTime);
                response.setLastMove("TIMEOUT");
                response.setEndReason("TIMEOUT");
                response.setResult(result);
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