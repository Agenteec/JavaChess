package ru.agenteec.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    private final StringRedisTemplate redisTemplate;
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public GameService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getOrCreateGame(String gameId) {
        String key = "chess:game:" + gameId;
        String fen = redisTemplate.opsForValue().get(key);
        if (fen == null) {
            redisTemplate.opsForValue().set(key, START_FEN, 1, TimeUnit.DAYS); // Игра хранится 24 часа
            return START_FEN;
        }
        return fen;
    }

    public String makeMove(String gameId, String from, String to) {
        String key = "chess:game:" + gameId;
        String currentFen = redisTemplate.opsForValue().get(key);
        if (currentFen == null) {
            currentFen = START_FEN;
        }

        Board board = new Board();
        board.loadFromFen(currentFen);

        Square fromSquare = Square.valueOf(from.toUpperCase());
        Square toSquare = Square.valueOf(to.toUpperCase());

        Move move = new Move(fromSquare, toSquare);

        if (!board.legalMoves().contains(move)) {
            throw new IllegalArgumentException("Нелегальный ход: " + from + " на " + to);
        }

        board.doMove(move);

        String newFen = board.getFen();
        redisTemplate.opsForValue().set(key, newFen, 1, TimeUnit.DAYS);

        return newFen;
    }

    public Board getBoardState(String gameId) {
        String key = "chess:game:" + gameId;
        String currentFen = redisTemplate.opsForValue().get(key);
        if (currentFen == null) {
            currentFen = START_FEN;
        }
        Board board = new Board();
        board.loadFromFen(currentFen);
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
    public void initGameTime(String gameId) {
        String whiteTimeKey = "chess:game:" + gameId + ":time:white";
        String blackTimeKey = "chess:game:" + gameId + ":time:black";
        String lastMoveKey = "chess:game:" + gameId + ":last_move_time";

        redisTemplate.opsForValue().setIfAbsent(whiteTimeKey, "600", 1, TimeUnit.DAYS); // 10 минут
        redisTemplate.opsForValue().setIfAbsent(blackTimeKey, "600", 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().setIfAbsent(lastMoveKey, String.valueOf(System.currentTimeMillis()), 1, TimeUnit.DAYS);
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
        redisTemplate.opsForValue().set(activeKey, String.valueOf(timeLeft), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(lastMoveKey, String.valueOf(now), 1, TimeUnit.DAYS);

        long whiteTime = Long.parseLong(redisTemplate.opsForValue().get(whiteTimeKey));
        long blackTime = Long.parseLong(redisTemplate.opsForValue().get(blackTimeKey));

        return new long[]{whiteTime, blackTime};
    }
}