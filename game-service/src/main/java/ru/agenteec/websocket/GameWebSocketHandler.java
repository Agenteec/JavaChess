package ru.agenteec.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.agenteec.dto.GameMessage;
import ru.agenteec.dto.GameResponse;
import ru.agenteec.service.GameService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameService gameService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            GameMessage payload = objectMapper.readValue(message.getPayload(), GameMessage.class);
            String gameId = payload.getGameId();

            if (gameId == null || gameId.isBlank()) {
                sendError(session, "Укажите gameId");
                return;
            }

            switch (payload.getAction().toUpperCase()) {
                case "JOIN":
                    handleJoin(session, gameId);
                    break;
                case "MOVE":
                    handleMove(payload);
                    break;
                case "CHAT":
                    handleChat(payload);
                    break;
                default:
                    sendError(session, "Неизвестное действие: " + payload.getAction());
            }
        } catch (Exception e) {
            sendError(session, "Ошибка обработки: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocketSession session, String gameId) throws IOException {
        roomSessions.computeIfAbsent(gameId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
        sessionRooms.put(session.getId(), gameId);

        String fen = gameService.getOrCreateGame(gameId);
        Board board = gameService.getBoardState(gameId);

        GameResponse response = new GameResponse(
                "STATE",
                fen,
                board.getSideToMove().toString(),
                board.isMated(),
                board.isDraw()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void handleMove(GameMessage payload) throws IOException {
        String gameId = payload.getGameId();
        try {
            String newFen = gameService.makeMove(gameId, payload.getFrom(), payload.getTo());
            Board board = gameService.getBoardState(gameId);

            GameResponse response = new GameResponse(
                    "STATE",
                    newFen,
                    board.getSideToMove().toString(),
                    board.isMated(),
                    board.isDraw()
            );
            response.setLastMove(payload.getFrom() + "-" + payload.getTo());

            broadcastToRoom(gameId, response);
        } catch (Exception e) {
            broadcastToRoom(gameId, new GameResponse("ERROR", e.getMessage()));
        }
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
}