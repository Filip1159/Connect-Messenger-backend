package com.example.connectmessenger.config;

import com.example.connectmessenger.service.UserService;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserService userService;
    private final CustomProperties properties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.addExposedHeader("Authentication");
        configuration.addExposedHeader("X-Request-Id");
        configuration.addExposedHeader("Retry-After");
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList(
                "Accept", "Origin", "Content-Type", "User-Agent",
                "Cache-Control", "Authorization", "X-Req", "X-Request-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public TokenIssuer tokenIssuer() {
        return new TokenIssuer(properties.secret(), properties.jwtExpirationTime());
    }

    @Bean
    public LoginThrottle loginThrottle() {
        return new LoginThrottle(properties.loginMaxFailures(), properties.loginFailureWindow(),
                properties.loginLockout(), Clock.systemUTC());
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        // UserService is the single UserDetailsService; passwords use the default {bcrypt} delegating encoder
        return new ProviderManager(new DaoAuthenticationProvider(userService));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // stateless API authenticated by a bearer token in a header: no cookies, so no CSRF exposure
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()  // keep real error codes
                        .requestMatchers("/api/v1/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()  // load balancer / orchestrator probes
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()  // registration
                        .anyRequest().authenticated())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilter(new JsonObjectAuthenticationFilter(tokenIssuer(), loginThrottle(), authenticationManager()))
                .addFilterAfter(new JwtAuthorizationFilter(properties.secret(), userService, tokenIssuer()), JsonObjectAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
