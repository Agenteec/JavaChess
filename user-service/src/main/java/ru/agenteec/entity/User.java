package ru.agenteec.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    private String verificationToken;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    private boolean verified = false;

    private String resetToken;
    private java.time.LocalDateTime resetTokenExpiry;
    private java.time.LocalDateTime verificationTokenExpiry;

    private int ratingBullet = 1500;
    private int ratingBlitz = 1500;
    private int ratingRapid = 1500;
    private int ratingClassical = 1500;
    private int ratingCorrespondence = 1500;

    private int gamesBullet = 0;
    private int gamesBlitz = 0;
    private int gamesRapid = 0;
    private int gamesClassical = 0;
    private int gamesCorrespondence = 0;
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getRatingBullet() { return ratingBullet; }
    public void setRatingBullet(int ratingBullet) { this.ratingBullet = ratingBullet; }
    public int getRatingBlitz() { return ratingBlitz; }
    public void setRatingBlitz(int ratingBlitz) { this.ratingBlitz = ratingBlitz; }
    public int getRatingRapid() { return ratingRapid; }
    public void setRatingRapid(int ratingRapid) { this.ratingRapid = ratingRapid; }
    public int getRatingClassical() { return ratingClassical; }
    public void setRatingClassical(int ratingClassical) { this.ratingClassical = ratingClassical; }
    public int getRatingCorrespondence() { return ratingCorrespondence; }
    public void setRatingCorrespondence(int ratingCorrespondence) { this.ratingCorrespondence = ratingCorrespondence; }

    public int getGamesBullet() { return gamesBullet; }
    public void setGamesBullet(int gamesBullet) { this.gamesBullet = gamesBullet; }
    public int getGamesBlitz() { return gamesBlitz; }
    public void setGamesBlitz(int gamesBlitz) { this.gamesBlitz = gamesBlitz; }
    public int getGamesRapid() { return gamesRapid; }
    public void setGamesRapid(int gamesRapid) { this.gamesRapid = gamesRapid; }
    public int getGamesClassical() { return gamesClassical; }
    public void setGamesClassical(int gamesClassical) { this.gamesClassical = gamesClassical; }
    public int getGamesCorrespondence() { return gamesCorrespondence; }
    public void setGamesCorrespondence(int gamesCorrespondence) { this.gamesCorrespondence = gamesCorrespondence; }

    public String getEmail() {return email;}
    public void setEmail(String email){this.email = email;}

    public boolean isVerified() {return verified;}
    public void setVerified(boolean verified) {this.verified = verified;}

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public java.time.LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(java.time.LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }
    public java.time.LocalDateTime getVerificationTokenExpiry() { return verificationTokenExpiry; }
    public void setVerificationTokenExpiry(java.time.LocalDateTime verificationTokenExpiry) { this.verificationTokenExpiry = verificationTokenExpiry; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }


}