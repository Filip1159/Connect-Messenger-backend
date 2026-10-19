package com.example.connectmessenger.config;

import com.example.connectmessenger.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

public class JsonObjectAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TokenIssuer tokenIssuer;
    private final LoginThrottle throttle;

    public JsonObjectAuthenticationFilter(TokenIssuer tokenIssuer,
                                          LoginThrottle throttle,
                                          AuthenticationManager authenticationManager) {
        this.tokenIssuer = tokenIssuer;
        this.throttle = throttle;
        setAuthenticationManager(authenticationManager);
        setRequiresAuthenticationRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/login"));
        setAuthenticationFailureHandler(JsonObjectAuthenticationFilter::onFailure);
    }

    private static void onFailure(HttpServletRequest request, HttpServletResponse response,
                                  AuthenticationException exception) throws IOException {
        if (exception instanceof TooManyLoginAttemptsException tooMany) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, tooMany.getRetryAfter().toSeconds())));
            response.sendError(429);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        try {
            LoginCredentials credentials = MAPPER.readValue(request.getReader(), LoginCredentials.class);

            if (credentials == null || credentials.getUsername() == null || credentials.getPassword() == null) {
                throw new AuthenticationServiceException("Missing credentials");
            }

            String ip = request.getRemoteAddr();
            String username = credentials.getUsername();
            Duration wait = throttle.retryAfter(ip, username);
            if (!wait.isZero()) throw new TooManyLoginAttemptsException(wait);

            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    username,
                    credentials.getPassword()
            );

            try {
                Authentication result = getAuthenticationManager().authenticate(token);
                throttle.recordSuccess(username);
                return result;
            } catch (BadCredentialsException e) {
                throttle.recordFailure(ip, username);
                throw e;
            }
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

        response.addHeader("Authentication", "Bearer " + tokenIssuer.issue(user));
    }
}
