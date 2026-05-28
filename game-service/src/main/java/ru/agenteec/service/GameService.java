package ru.agenteec.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    private final StringRedisTemplate redisTemplate;
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public GameService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getOrCreateGame(String gameId) {
        String key = "chess:game:" + gameId;
        String fen = redisTemplate.opsForValue().get(key);
        if (fen == null) {
            redisTemplate.opsForValue().set(key, START_FEN, 1, TimeUnit.DAYS);
            return START_FEN;
        }
        return fen;
    }

    public List<String> makeMove(String gameId, String fromStr, String toStr) {
        Board board = getBoardState(gameId);

        com.github.bhlangonijr.chesslib.Square from = com.github.bhlangonijr.chesslib.Square.valueOf(fromStr.toUpperCase());
        com.github.bhlangonijr.chesslib.Square to = com.github.bhlangonijr.chesslib.Square.valueOf(toStr.toUpperCase());
        com.github.bhlangonijr.chesslib.move.Move move = new com.github.bhlangonijr.chesslib.move.Move(from, to);

        if (!board.legalMoves().contains(move)) {
            throw new IllegalArgumentException("Нелегальный ход: " + fromStr + " на " + toStr);
        }

        board.doMove(move);

        String movesKey = "chess:game:" + gameId + ":moves";
        redisTemplate.opsForList().rightPush(movesKey, fromStr + toStr);
        redisTemplate.expire(movesKey, 1, TimeUnit.DAYS);

        String fenKey = "chess:game:" + gameId;
        redisTemplate.opsForValue().set(fenKey, board.getFen(), 1, TimeUnit.DAYS);

        return getGameMoves(gameId);
    }

    public Board getBoardState(String gameId) {
        Board board = new Board();
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        List<String> moves = getGameMoves(gameId);
        for (String moveStr : moves) {
            com.github.bhlangonijr.chesslib.Square from = com.github.bhlangonijr.chesslib.Square.valueOf(moveStr.substring(0, 2).toUpperCase());
            com.github.bhlangonijr.chesslib.Square to = com.github.bhlangonijr.chesslib.Square.valueOf(moveStr.substring(2, 4).toUpperCase());
            board.doMove(new com.github.bhlangonijr.chesslib.move.Move(from, to));
        }
        return board;
    }
    public void assignPlayerColor(String gameId, String username) {
        String whiteKey = "chess:game:" + gameId + ":white";
        String blackKey = "chess:game:" + gameId + ":black";

        String white = redisTemplate.opsForValue().get(whiteKey);
        if (white == null) {
            redisTemplate.opsForValue().set(whiteKey, username, 1, TimeUnit.DAYS);
        } else if (!white.equals(username)) {
            redisTemplate.opsForValue().set(blackKey, username, 1, TimeUnit.DAYS);
        }
    }

    public String getPlayerColor(String gameId, String color) {
        return redisTemplate.opsForValue().get("chess:game:" + gameId + ":" + color.toLowerCase());
    }
    public void initGameTime(String gameId, int minutes, int increment) {
        String whiteTimeKey = "chess:game:" + gameId + ":time:white";
        String blackTimeKey = "chess:game:" + gameId + ":time:black";
        String lastMoveKey = "chess:game:" + gameId + ":last_move_time";
        String incrementKey = "chess:game:" + gameId + ":increment";

        redisTemplate.opsForValue().setIfAbsent(whiteTimeKey, String.valueOf(minutes * 60), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().setIfAbsent(blackTimeKey, String.valueOf(minutes * 60), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().setIfAbsent(lastMoveKey, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().setIfAbsent(incrementKey, String.valueOf(increment), 1, TimeUnit.DAYS);

        redisTemplate.delete("chess:game:" + gameId + ":moves");
    }

    public void initGameTime(String gameId) {
        initGameTime(gameId, 10, 0);
    }
    public long[] getGameTimes(String gameId) {
        String whiteTimeKey = "chess:game:" + gameId + ":time:white";
        String blackTimeKey = "chess:game:" + gameId + ":time:black";

        String wStr = redisTemplate.opsForValue().get(whiteTimeKey);
        String bStr = redisTemplate.opsForValue().get(blackTimeKey);

        long whiteTime = wStr != null ? Long.parseLong(wStr) : 600;
        long blackTime = bStr != null ? Long.parseLong(bStr) : 600;

        return new long[]{whiteTime, blackTime};
    }
    public List<String> getGameMoves(String gameId) {
        String key = "chess:game:" + gameId + ":moves";
        List<String> moves = redisTemplate.opsForList().range(key, 0, -1);

        if (moves == null || moves.isEmpty()) {
            try {
                String userServiceUrl = "http://localhost:8081/api/v1/internal/games/" + gameId;
                Map<?, ?> history = restTemplate.getForObject(userServiceUrl, Map.class);

                if (history != null && history.get("pgn") != null) {
                    String pgn = (String) history.get("pgn");
                    pgn = pgn.replace("[", "").replace("]", "").replaceAll("\\s+", "");
                    if (!pgn.isBlank()) {
                        return Arrays.asList(pgn.split(","));
                    }
                }
            } catch (Exception e) {
                System.out.println("Партия не найдена в архиве PostgreSQL: " + e.getMessage());
            }
        }
        return moves != null ? moves : new ArrayList<>();
    }
    public void setGameCategory(String gameId, String category) {
        redisTemplate.opsForValue().set("chess:game:" + gameId + ":category", category.toUpperCase(), 1, TimeUnit.DAYS);
    }

    public String getGameCategory(String gameId) {
        String cat = redisTemplate.opsForValue().get("chess:game:" + gameId + ":category");
        return cat != null ? cat.toUpperCase() : "RAPID";
    }
    public long[] updateTimeOnMove(String gameId, String activeColor) {
        String whiteTimeKey = "chess:game:" + gameId + ":time:white";
        String blackTimeKey = "chess:game:" + gameId + ":time:black";
        String lastMoveKey = "chess:game:" + gameId + ":last_move_time";

        long now = System.currentTimeMillis();
        String lastMoveStr = redisTemplate.opsForValue().get(lastMoveKey);
        long lastMoveTime = lastMoveStr != null ? Long.parseLong(lastMoveStr) : now;

        long elapsedSeconds = (now - lastMoveTime) / 1000;

        String activeKey = activeColor.equalsIgnoreCase("WHITE") ? whiteTimeKey : blackTimeKey;
        String currentTimeStr = redisTemplate.opsForValue().get(activeKey);
        long timeLeft = currentTimeStr != null ? Long.parseLong(currentTimeStr) : 600;

        timeLeft = Math.max(0, timeLeft - elapsedSeconds);

        if (timeLeft > 0) {
            String incStr = redisTemplate.opsForValue().get("chess:game:" + gameId + ":increment");
            int increment = incStr != null ? Integer.parseInt(incStr) : 0;
            timeLeft += increment;
        }

        redisTemplate.opsForValue().set(activeKey, String.valueOf(timeLeft), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(lastMoveKey, String.valueOf(now), 1, TimeUnit.DAYS);

        long whiteTime = Long.parseLong(redisTemplate.opsForValue().get(whiteTimeKey));
        long blackTime = Long.parseLong(redisTemplate.opsForValue().get(blackTimeKey));

        return new long[]{whiteTime, blackTime};
    }
}