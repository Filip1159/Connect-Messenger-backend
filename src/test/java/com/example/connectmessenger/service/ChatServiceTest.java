package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Chat;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.CreateChatRequest;
import com.example.connectmessenger.repo.ChatRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    ChatRepo chatRepo;
    @Mock
    StatusRepo statusRepo;
    @Mock
    UserRepo userRepo;
    @Mock
    com.example.connectmessenger.repo.MessageRepo messageRepo;
    @InjectMocks
    ChatService chatService;

    @Test
    void assertMemberPassesForMember() {
        when(chatRepo.existsByIdAndUsers_Id(1L, 2L)).thenReturn(true);

        assertThatCode(() -> chatService.assertMember(1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void assertMemberRejectsNonMember() {
        when(chatRepo.existsByIdAndUsers_Id(1L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.assertMember(1L, 3L)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assertMemberRejectsNullIdsWithoutQuerying() {
        assertThatThrownBy(() -> chatService.assertMember(null, 1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> chatService.assertMember(1L, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(chatRepo);
    }

    @Test
    void getChatByIdThrows404ForUnknownChat() {
        when(chatRepo.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getChatById(5L)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createChatRejectsInvalidUserLists() {
        for (List<Long> ids : new List[]{null, List.of(), List.of(1L), List.of(1L, 1L)}) {
            assertThatThrownBy(() -> chatService.createChat(new CreateChatRequest(ids)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(chatRepo, never()).save(any());
    }

    @Test
    void createChatThrows404ForUnknownUser() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.createChat(new CreateChatRequest(List.of(1L, 99L))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chatRepo, never()).save(any());
    }

    @Test
    void createChatSavesChatAndOneStatusPerUser() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(userRepo.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(userRepo.findById(3L)).thenReturn(Optional.of(user(3L)));
        when(chatRepo.save(any(Chat.class))).thenAnswer(inv -> {
            Chat chat = inv.getArgument(0);
            chat.setId(10L);
            return chat;
        });

        when(statusRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = chatService.createChat(new CreateChatRequest(List.of(1L, 2L, 3L)));

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getUsers()).hasSize(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Status>> statuses = ArgumentCaptor.forClass(List.class);
        verify(statusRepo).saveAll(statuses.capture());
        assertThat(statuses.getValue()).hasSize(3)
                .allSatisfy(s -> assertThat(s.getId().getChatId()).isEqualTo(10L))
                .extracting(s -> s.getId().getUserId()).containsExactly(1L, 2L, 3L);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }
}
