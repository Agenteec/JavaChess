package ru.agenteec.dto;

public class GameMessage {
    private String action;
    private String gameId;
    private String username;
    private String from;
    private String to;
    private String message;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}