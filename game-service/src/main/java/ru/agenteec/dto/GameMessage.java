package ru.agenteec.dto;

public class GameMessage {
    private String action;
    private String gameId;
    private String username;
    private String from;
    private String to;
    private String message;
    private Integer minutes;
    private Integer increment;
    private String category;
    private String token;

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
    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }
    public Integer getIncrement() { return increment; }
    public void setIncrement(Integer increment) { this.increment = increment; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}