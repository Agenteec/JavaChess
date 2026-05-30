package ru.agenteec.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_history")
public class GameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gameId;
    private String whitePlayer;
    private String blackPlayer;
    private String result;
    private String category;

    @Column(length = 2000)
    private String pgn;

    private Integer whiteRating;
    private Integer blackRating;
    private Integer whiteRatingChange;
    private Integer blackRatingChange;

    private LocalDateTime endedAt = LocalDateTime.now();

    public GameHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getWhitePlayer() { return whitePlayer; }
    public void setWhitePlayer(String whitePlayer) { this.whitePlayer = whitePlayer; }
    public String getBlackPlayer() { return blackPlayer; }
    public void setBlackPlayer(String blackPlayer) { this.blackPlayer = blackPlayer; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getPgn() { return pgn; }
    public void setPgn(String pgn) { this.pgn = pgn; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getWhiteRating() { return whiteRating; }
    public void setWhiteRating(Integer whiteRating) { this.whiteRating = whiteRating; }
    public Integer getBlackRating() { return blackRating; }
    public void setBlackRating(Integer blackRating) { this.blackRating = blackRating; }
    public Integer getWhiteRatingChange() { return whiteRatingChange; }
    public void setWhiteRatingChange(Integer whiteRatingChange) { this.whiteRatingChange = whiteRatingChange; }
    public Integer getBlackRatingChange() { return blackRatingChange; }
    public void setBlackRatingChange(Integer blackRatingChange) { this.blackRatingChange = blackRatingChange; }
}