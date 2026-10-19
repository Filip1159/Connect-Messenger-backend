package com.example.connectmessenger.controller;

import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.dto.StatusDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StatusControllerTest extends ControllerComponentTest {
    @MockitoSpyBean
    SimpMessagingTemplate template;


    @Test
    void statusCanBeUpdatedForYourselfInYourOwnChat() {
        asJson(janek(), "{\"id\":{\"userId\":1,\"chatId\":1},\"messageId\":1,\"time\":\"2021-07-07T22:24:59\"}")
                .put("/status").then().statusCode(200);

        verify(statusRepo).save(any(Status.class));
        verify(template).convertAndSend(eq("/topic/status/1"), any(StatusDto.class));
        verify(template).convertAndSend(eq("/topic/messages/1"), any(StatusDto.class));
    }

    @Test
    void statusCannotBeUpdatedForSomeoneElseOrInForeignChat() {
        asJson(janek(), "{\"id\":{\"userId\":2,\"chatId\":1},\"messageId\":1}").put("/status").then().statusCode(403);
        asJson(janek(), "{\"id\":{\"userId\":1,\"chatId\":3},\"messageId\":18}").put("/status").then().statusCode(403);
        asJson(janek(), "{}").put("/status").then().statusCode(400);

        verify(statusRepo, never()).save(any());
    }

    @Test
    void userIdAndTimeAreOptionalAndTakenFromTheServer() {
        asJson(janek(), "{\"id\":{\"chatId\":1},\"messageId\":1}").put("/status").then().statusCode(200);

        var saved = org.mockito.ArgumentCaptor.forClass(Status.class);
        verify(statusRepo).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1L, saved.getValue().getId().getUserId());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getValue().getTime());
    }

    @Test
    void statusMustPointAtAnExistingMessageOfThatChat() {
        asJson(janek(), "{\"id\":{\"userId\":1,\"chatId\":1},\"messageId\":13}").put("/status").then().statusCode(400);
        asJson(janek(), "{\"id\":{\"userId\":1,\"chatId\":1},\"messageId\":99999}").put("/status").then().statusCode(404);

        verify(statusRepo, never()).save(any());
    }
}
