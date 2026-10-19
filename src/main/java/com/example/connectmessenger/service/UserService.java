package com.example.connectmessenger.service;


import com.example.connectmessenger.model.User;
import com.example.connectmessenger.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepo userRepo;

    private static final int MIN_QUERY_LENGTH = 2;

    public List<User> getUsersByQuery(String text, Long excludedUserId) {
        String query = text.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return userRepo.findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(query, query, query)
                .stream()
                .filter(u -> !u.getId().equals(excludedUserId))
                .toList();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.getUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
