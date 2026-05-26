package ru.agenteec.dto;

public class AuthResponse {
    private String token;
    private String username;
    private int rating;

    public AuthResponse(String token, String username, int rating) {
        this.token = token;
        this.username = username;
        this.rating = rating;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public int getRating() { return rating; }
}