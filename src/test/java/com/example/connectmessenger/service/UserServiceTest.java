package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    UserRepo userRepo;
    @Mock
    com.example.connectmessenger.repo.UserBlockRepo userBlockRepo;
    @InjectMocks
    UserService userService;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void shortQueriesReturnNothingWithoutHittingTheDatabase() {
        assertThat(userService.getUsersByQuery("a", 1L)).isEmpty();
        assertThat(userService.getUsersByQuery("  b ", 1L)).isEmpty();
        verifyNoInteractions(userRepo);
    }

    @Test
    void searchTrimsQueryAndExcludesTheCaller() {
        when(userRepo.findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
                "jan", "jan", "jan")).thenReturn(List.of(user(1L), user(2L)));

        List<User> result = userService.getUsersByQuery("  jan ", 1L);

        assertThat(result).extracting(User::getId).containsExactly(2L);
    }

    @Test
    void searchHidesPeopleWhoBlockedTheCaller() {
        when(userRepo.findTop20ByUsernameContainingIgnoreCaseOrNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
                "jan", "jan", "jan")).thenReturn(List.of(user(2L), user(3L)));
        when(userBlockRepo.findByBlockedId(1L)).thenReturn(List.of(
                com.example.connectmessenger.model.UserBlock.builder().blockerId(3L).blockedId(1L).build()));

        assertThat(userService.getUsersByQuery("jan", 1L)).extracting(User::getId).containsExactly(2L);
    }

    @Test
    void loadUserByUsernameThrowsUsernameNotFound() {
        when(userRepo.getUserByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsernameReturnsUser() {
        User u = user(3L);
        when(userRepo.getUserByUsername("mrsmith")).thenReturn(Optional.of(u));

        assertThat(userService.loadUserByUsername("mrsmith")).isSameAs(u);
        verify(userRepo).getUserByUsername("mrsmith");
    }
}
