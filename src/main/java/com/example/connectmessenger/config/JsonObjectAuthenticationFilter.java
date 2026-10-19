package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.connectmessenger.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Date;

public class JsonObjectAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Algorithm algorithm;
    private final Long expirationTime;
    private final AuthenticationManager authenticationManager;

    public JsonObjectAuthenticationFilter(String secret,
                                          Long expirationTime,
                                          AuthenticationManager authenticationManager) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationTime = expirationTime;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        try {
            LoginCredentials credentials = MAPPER.readValue(request.getReader(), LoginCredentials.class);

            if (credentials == null || credentials.getUsername() == null || credentials.getPassword() == null) {
                throw new AuthenticationServiceException("Missing credentials");
            }

            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    credentials.getUsername(),
                    credentials.getPassword()
            );

            return authenticationManager.authenticate(token);
        } catch (IOException | JacksonException e) {
            throw new AuthenticationServiceException("Malformed login request", e);
        }
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authResult) {

        User user = (User) authResult.getPrincipal();

        String token = JWT.create()
                .withClaim("id", user.getId())
                .withClaim("username", user.getUsername())
                .withClaim("name", user.getName())
                .withClaim("surname", user.getSurname())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                .sign(algorithm);
        response.addHeader("Authentication", "Bearer " + token);
    }
}