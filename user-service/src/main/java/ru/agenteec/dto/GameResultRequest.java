package ru.agenteec.dto;

public class GameResultRequest {
    private String gameId;
    private String whitePlayer;
    private String blackPlayer;
    private String result;
    private String pgn;
    private String category;

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
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}