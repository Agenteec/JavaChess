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
}