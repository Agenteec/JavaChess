package ru.agenteec.dto;

public class GameResponse {
    private String type;
    private String fen;
    private String turn;
    private String lastMove;
    private boolean isMated;
    private boolean isDraw;
    private String message;
    private String sender;

    public GameResponse() {}

    public GameResponse(String type, String fen, String turn, boolean isMated, boolean isDraw) {
        this.type = type;
        this.fen = fen;
        this.turn = turn;
        this.isMated = isMated;
        this.isDraw = isDraw;
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
}