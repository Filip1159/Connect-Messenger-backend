package com.example.connectmessenger.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.connectmessenger.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final String secret;
    private final UserDetailsService userDetailsService;
    private final TokenIssuer tokenIssuer;

    public JwtAuthorizationFilter(String secret,
                                  UserDetailsService userDetailsService,
                                  TokenIssuer tokenIssuer) {
        this.userDetailsService = userDetailsService;
        this.secret = secret;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            /* token problem */
            filterChain.doFilter(request, response);
            return;
        }

        token = token.replace("Bearer ", "");
        String username;
        Date expiresAt;
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            username = jwt.getClaim("username").asString();
            expiresAt = jwt.getExpiresAt();
        } catch (JWTVerificationException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (username == null) {
            /* user received through token doesn't exist */
            filterChain.doFilter(request, response);
            return;
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException | java.util.NoSuchElementException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(auth);
        // sliding expiry: an active user gets a fresh token once half of the lifetime of this one is gone
        if (userDetails instanceof User user && expiresAt != null
                && expiresAt.getTime() - System.currentTimeMillis() < tokenIssuer.getExpirationTime() / 2) {
            response.setHeader("Authentication", "Bearer " + tokenIssuer.issue(user));
        }
        filterChain.doFilter(request, response);
    }
}
