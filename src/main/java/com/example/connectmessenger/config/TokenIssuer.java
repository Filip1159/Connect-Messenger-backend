package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.connectmessenger.model.User;

import java.util.Date;

/** Creates the signed tokens (the same claims at login and when a token is renewed). */
public class TokenIssuer {
    private final Algorithm algorithm;
    private final long expirationTime;

    public TokenIssuer(String secret, long expirationTime) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationTime = expirationTime;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public String issue(User user) {
        return JWT.create()
                .withClaim("id", user.getId())
                .withClaim("username", user.getUsername())
                .withClaim("name", user.getName())
                .withClaim("surname", user.getSurname())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                .sign(algorithm);
    }
}
