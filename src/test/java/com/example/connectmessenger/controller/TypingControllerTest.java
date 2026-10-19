package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.TypingDto;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TypingControllerTest {
    SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    TypingController controller = new TypingController(template);

    @Test
    void relaysWhoIsTypingToTheChatTopic() {
        User user = new User();
        user.setId(7L);

        controller.typing(5L, new UsernamePasswordAuthenticationToken(user, null));

        verify(template).convertAndSend("/topic/typing/5", new TypingDto(5L, 7L));
    }

    @Test
    void ignoresUnauthenticatedSenders() {
        controller.typing(5L, null);

        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
