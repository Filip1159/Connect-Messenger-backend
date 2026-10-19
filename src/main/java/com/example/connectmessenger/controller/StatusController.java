package com.example.connectmessenger.controller;

import com.example.connectmessenger.config.CustomProperties;
import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.User;
import com.example.connectmessenger.model.dto.StatusDto;
import com.example.connectmessenger.repo.MessageRepo;
import com.example.connectmessenger.repo.StatusRepo;
import com.example.connectmessenger.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class StatusController {

    private final StatusRepo statusRepo;
    private final MessageRepo messageRepo;
    private final ChatService chatService;
    private final SimpMessagingTemplate template;
    private final CustomProperties properties;

    @PutMapping("/status")
    public void updateStatus(@RequestBody StatusDto request, @AuthenticationPrincipal User principal) {
        if (request.id() == null || request.id().chatId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id.chatId is required");
        }
        // id.userId is optional: the status always belongs to the authenticated user
        if (request.id().userId() != null && !principal.getId().equals(request.id().userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot update another user's status");
        }
        Long chatId = request.id().chatId();
        chatService.assertMember(chatId, principal.getId());

        if (request.messageId() != null) {
            Message displayed = messageRepo.findById(request.messageId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
            if (!chatId.equals(displayed.getChatId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this chat");
            }
        }

        var own = new StatusDto(new StatusDto.Id(principal.getId(), chatId), request.messageId(),
                request.time() == null ? LocalDateTime.now() : request.time());
        Status saved = statusRepo.save(own.toEntity());
        var dto = StatusDto.from(saved);
        template.convertAndSend("/topic/status/" + chatId, dto);
        if (properties.legacyStatusBroadcast()) {
            // old clients listen for statuses on the messages topic; switch off once they use /topic/status
            template.convertAndSend("/topic/messages/" + chatId, dto);
        }
    }
}
