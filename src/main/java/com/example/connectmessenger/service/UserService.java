package com.example.connectmessenger.service;


import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.UserBlock;
import com.example.connectmessenger.repo.UserBlockRepo;
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
    private final UserBlockRepo userBlockRepo;

    private static final int MIN_QUERY_LENGTH = 2;

    public List<User> getUsersByQuery(String text, Long excludedUserId) {
        String query = text.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        // people who blocked the searcher don't show up (they can't be messaged anyway)
        var blockedBy = userBlockRepo.findByBlockedId(excludedUserId).stream().map(UserBlock::getBlockerId).toList();
        return userRepo.findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(query, query, query)
                .stream()
                .filter(u -> !u.getId().equals(excludedUserId) && !blockedBy.contains(u.getId()))
                .toList();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.getUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
