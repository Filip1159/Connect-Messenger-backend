package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.dto.CreateMessageRequest;
import com.example.connectmessenger.repo.MessageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final int MAX_TEXT_LENGTH = 1000;

    private final MessageRepo messageRepo;
    private final AwsS3Client s3Client;

    public Message getMessageById(Long id) {
        return messageRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    /**
     * The upload runs after the insert but inside the transaction, so a failed upload rolls the message back.
     */
    @Transactional
    public Message addMessage(CreateMessageRequest request) {
        if (request.chatId() == null || request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId and type are required");
        }

        String content;
        AwsS3Client.ImageInfo image = null;
        if (request.type() == MessageType.FILE) {
            image = s3Client.detectImage(request.fileContent());
            content = request.chatId() + "_" + UUID.randomUUID() + "." + image.extension();
        } else {
            content = request.textContent();
            if (content == null || content.isBlank() || content.length() > MAX_TEXT_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Text must be between 1 and " + MAX_TEXT_LENGTH + " characters");
            }
        }

        var saved = messageRepo.save(Message.builder()
                .chatId(request.chatId())
                .userId(request.userId())
                .time(LocalDateTime.now())
                .type(request.type())
                .content(content)
                .build());

        if (image != null) {
            s3Client.putFile(content, request.fileContent(), image);
        }
        return saved;
    }
}
