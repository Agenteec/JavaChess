package ru.agenteec.dto;

import java.util.List;

public class GameResponse {
    private String type;
    private String fen;
    private String turn;
    private String lastMove;
    private boolean isMated;
    private boolean isDraw;
    private String message;
    private String sender;
    private String endReason;
    private String result;
    private List<String> moves;
    private String category;
    private Integer whiteRatingChange;
    private Integer blackRatingChange;
    private Integer whiteNewRating;
    private Integer blackNewRating;



    public List<String> getMoves() { return moves; }
    public void setMoves(List<String> moves) { this.moves = moves; }
    private long whiteTimeLeft;
    private long blackTimeLeft;

    public GameResponse() {}

    public GameResponse(String type, String fen, String turn, boolean isMated, boolean isDraw, long whiteTimeLeft, long blackTimeLeft) {
        this.type = type;
        this.fen = fen;
        this.turn = turn;
        this.isMated = isMated;
        this.isDraw = isDraw;
        this.whiteTimeLeft = whiteTimeLeft;
        this.blackTimeLeft = blackTimeLeft;
    }

    public GameResponse(String type, String sender, String message) {
        this.type = type;
        this.sender = sender;
        this.message = message;
    }

    public GameResponse(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() { return type; }
    public String getFen() { return fen; }
    public String getTurn() { return turn; }
    public String getLastMove() { return lastMove; }
    public void setLastMove(String lastMove) { this.lastMove = lastMove; }
    public boolean isMated() { return isMated; }
    public boolean isDraw() { return isDraw; }
    public String getMessage() { return message; }
    public String getSender() { return sender; }
    public long getWhiteTimeLeft() { return whiteTimeLeft; }
    public long getBlackTimeLeft() { return blackTimeLeft; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public void setTurn(String turn) { this.turn = turn; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getWhiteRatingChange() { return whiteRatingChange; }
    public void setWhiteRatingChange(Integer whiteRatingChange) { this.whiteRatingChange = whiteRatingChange; }
    public Integer getBlackRatingChange() { return blackRatingChange; }
    public void setBlackRatingChange(Integer blackRatingChange) { this.blackRatingChange = blackRatingChange; }
    public Integer getWhiteNewRating() { return whiteNewRating; }
    public void setWhiteNewRating(Integer whiteNewRating) { this.whiteNewRating = whiteNewRating; }
    public Integer getBlackNewRating() { return blackNewRating; }
    public void setBlackNewRating(Integer blackNewRating) { this.blackNewRating = blackNewRating; }
}