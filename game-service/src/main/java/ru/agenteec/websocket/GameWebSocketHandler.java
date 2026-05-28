package ru.agenteec.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.MoveBackup;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();
    private final Map<String, Queue<WebSocketSession>> matchmakingQueues = new ConcurrentHashMap<>();

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public GameWebSocketHandler(GameService gameService) {
        this.gameService = gameService;
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
                    handleJoin(session, payload.getGameId(), payload.getUsername());
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

                gameService.setGameCategory(gameUuid, category);

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

                Map<String, String> redirectData = Map.of(
                        "type", "REDIRECT",
                        "url", "play.html?room=" + gameUuid
                );
                String json = objectMapper.writeValueAsString(redirectData);

                player1.sendMessage(new TextMessage(json));
                player2.sendMessage(new TextMessage(json));
            }
        }
    }

    private void handleJoin(WebSocketSession session, String gameId, String username) throws IOException {
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
                String url = "http://localhost:8081/api/v1/internal/games/" + gameId;
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
            gameService.initGameTime(gameId);

            Object minsAttr = session.getAttributes().get("minutes");
            int mins = minsAttr instanceof Integer ? (Integer) minsAttr : 10;

            String category = "RAPID";
            if (mins < 3) category = "BULLET";
            else if (mins < 10) category = "BLITZ";
            else if (mins > 30) category = "CLASSICAL";

            gameService.setGameCategory(gameId, category);
            white = username;
        } else if (black == null && !username.equals(white) && !isArchived) {
            gameService.assignPlayerColor(gameId, username);
            black = username;
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

        Map<String, Object> meta = new HashMap<>();
        meta.put("role", isArchived ? "SPECTATOR" : role);
        meta.put("whitePlayer", white != null ? white : "Ожидание соперника...");
        meta.put("blackPlayer", black != null ? black : "Ожидание соперника...");
        meta.put("whiteRating", 1500);
        meta.put("blackRating", 1500);

        String jsonResponse = objectMapper.writeValueAsString(response);
        Map<String, Object> finalMap = objectMapper.readValue(jsonResponse, Map.class);
        finalMap.putAll(meta);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(finalMap)));
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
            gameService.makeMove(gameId, payload.getFrom(), payload.getTo());
            Board boardAfter = gameService.getBoardState(gameId);

            long[] times = gameService.updateTimeOnMove(gameId, currentTurn);
            long whiteTime = times[0];
            long blackTime = times[1];

            boolean isTimeout = (whiteTime <= 0 || blackTime <= 0);

            GameResponse response = new GameResponse(
                    "STATE", boardAfter.getFen(), boardAfter.getSideToMove().toString(),
                    boardAfter.isMated() || isTimeout, boardAfter.isDraw(),
                    whiteTime, blackTime
            );
            response.setLastMove(payload.getFrom().toLowerCase() + "-" + payload.getTo().toLowerCase());

            List<String> movesList = new ArrayList<>();
            for (String mStr : gameService.getGameMoves(gameId)) {
                movesList.add(mStr.toLowerCase());
            }
            response.setMoves(movesList);

            if (isTimeout) response.setLastMove("TIMEOUT");

            String endReason = null;
            if (boardAfter.isMated()) endReason = "MATE";
            else if (isTimeout) endReason = "TIMEOUT";
            else if (boardAfter.isDraw()) endReason = "DRAW";
            response.setEndReason(endReason);

            broadcastToRoom(gameId, response);

            if (boardAfter.isMated() || boardAfter.isDraw() || isTimeout) {
                String result = isTimeout ? (whiteTime <= 0 ? "BLACK_WON" : "WHITE_WON") : (boardAfter.getSideToMove().toString().equals("BLACK") ? "WHITE_WON" : "BLACK_WON");
                sendGameResultToUserService(gameId, boardAfter, result);
            }

        } catch (Exception e) {
            sendError(session, e.getMessage());
        }
    }

    private void handleSurrender(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        String username = payload.getUsername();
        if (username == null) username = (String) session.getAttributes().get("username");

        String white = gameService.getPlayerColor(gameId, "white");
        String black = gameService.getPlayerColor(gameId, "black");

        if (white == null || black == null) return;

        String winner = username.equals(white) ? black : white;
        String result = username.equals(white) ? "BLACK_WON" : "WHITE_WON";

        Board board = gameService.getBoardState(gameId);

        GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, 0, 0);
        response.setLastMove("TIMEOUT");
        response.setEndReason("SURRENDER");

        List<String> movesList = new ArrayList<>();
        for (String mStr : gameService.getGameMoves(gameId)) {
            movesList.add(mStr.toLowerCase());
        }
        response.setMoves(movesList);
        broadcastToRoom(gameId, response);

        sendGameResultToUserService(gameId, board, result);
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

    private void sendGameResultToUserService(String gameId, Board board, String result) {
        try {
            String white = gameService.getPlayerColor(gameId, "white");
            String black = gameService.getPlayerColor(gameId, "black");
            if (white == null || black == null) return;

            String category = gameService.getGameCategory(gameId);

            Map<String, Object> request = new HashMap<>();
            request.put("gameId", gameId);
            request.put("whitePlayer", white);
            request.put("blackPlayer", black);
            request.put("result", result);
            request.put("category", category);
            request.put("pgn", board.getHistory().toString());

            String userServiceUrl = "http://localhost:8081/api/v1/internal/games/complete";
            restTemplate.postForEntity(userServiceUrl, request, String.class);
            System.out.println(">>> Игра " + gameId + " завершена (" + category + "). Результаты отправлены!");
        } catch (Exception e) {
            System.err.println("Ошибка отправки результатов игры: " + e.getMessage());
        }
    }
    private void handleTimeout(WebSocketSession session, GameMessage payload) throws IOException {
        String gameId = payload.getGameId();

        long[] times = gameService.updateTimeOnMove(gameId, "WHITE");
        long whiteTime = times[0];
        long blackTime = times[1];

        if (whiteTime <= 0 || blackTime <= 0) {
            String white = gameService.getPlayerColor(gameId, "white");
            String black = gameService.getPlayerColor(gameId, "black");
            if (white == null || black == null) return;

            String result = (whiteTime <= 0) ? "BLACK_WON" : "WHITE_WON";
            Board board = gameService.getBoardState(gameId);

            GameResponse response = new GameResponse("STATE", board.getFen(), "WHITE", true, false, whiteTime, blackTime);
            response.setLastMove("TIMEOUT");
            response.setEndReason("TIMEOUT");
            response.setMoves(gameService.getGameMoves(gameId));
            broadcastToRoom(gameId, response);

            sendGameResultToUserService(gameId, board, result);
        }
    }
}