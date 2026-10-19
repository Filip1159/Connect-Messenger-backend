package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import com.example.connectmessenger.model.dto.CreateMessageRequest;
import com.example.connectmessenger.repo.MessageRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    MessageRepo messageRepo;
    @Mock
    AwsS3Client s3Client;
    @InjectMocks
    MessageService messageService;

    private static final LocalDateTime CLIENT_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private void stubSave() {
        when(messageRepo.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void textMessageIsSavedWithServerTimeAndNoUpload() {
        stubSave();
        var request = new CreateMessageRequest(1L, 2L, CLIENT_TIME, MessageType.TEXT, "hello", null, null);

        Message saved = messageService.addMessage(request);

        assertThat(saved.getContent()).isEqualTo("hello");
        assertThat(saved.getChatId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(2L);
        assertThat(saved.getTime()).isAfter(CLIENT_TIME);  // client supplied time is ignored
        verifyNoInteractions(s3Client);
    }

    @Test
    void invalidTextIsRejected() {
        for (String text : new String[]{null, "", "   ", "x".repeat(1001)}) {
            assertBadRequest(new CreateMessageRequest(1L, 2L, null, MessageType.TEXT, text, null, null));
        }
        verify(messageRepo, never()).save(any());
    }

    @Test
    void missingChatOrTypeIsRejected() {
        assertBadRequest(new CreateMessageRequest(null, 2L, null, MessageType.TEXT, "hi", null, null));
        assertBadRequest(new CreateMessageRequest(1L, 2L, null, null, "hi", null, null));
        verify(messageRepo, never()).save(any());
    }

    @Test
    void fileMessageGetsUniqueGeneratedNameAndIsUploaded() {
        stubSave();
        var file = new MockMultipartFile("fileContent", "../../evil.exe", "text/plain", new byte[]{1});
        var info = new AwsS3Client.ImageInfo("image/png", "png");
        when(s3Client.detectImage(file)).thenReturn(info);

        Message saved = messageService.addMessage(
                new CreateMessageRequest(5L, 2L, null, MessageType.FILE, null, file, null));

        assertThat(saved.getContent()).matches("5_[0-9a-f-]{36}\\.png");  // name/extension never come from the client
        verify(s3Client).putFile(saved.getContent(), file, info);
    }

    @Test
    void twoUploadsNeverShareAName() {
        stubSave();
        var file = new MockMultipartFile("fileContent", "a.png", "image/png", new byte[]{1});
        when(s3Client.detectImage(file)).thenReturn(new AwsS3Client.ImageInfo("image/png", "png"));
        var request = new CreateMessageRequest(5L, 2L, null, MessageType.FILE, null, file, null);

        assertThat(messageService.addMessage(request).getContent())
                .isNotEqualTo(messageService.addMessage(request).getContent());
    }

    @Test
    void invalidImageIsRejectedBeforeAnythingIsSaved() {
        var file = new MockMultipartFile("fileContent", "a.png", "image/png", new byte[]{1});
        when(s3Client.detectImage(file)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertBadRequest(new CreateMessageRequest(5L, 2L, null, MessageType.FILE, null, file, null));
        verify(messageRepo, never()).save(any());
        verify(s3Client, never()).putFile(anyString(), any(), any());
    }

    @Test
    void uploadFailurePropagatesSoTheTransactionRollsBack() {
        stubSave();
        var file = new MockMultipartFile("fileContent", "a.png", "image/png", new byte[]{1});
        var info = new AwsS3Client.ImageInfo("image/png", "png");
        when(s3Client.detectImage(file)).thenReturn(info);
        doThrow(new IllegalStateException("s3 down")).when(s3Client).putFile(anyString(), any(), any());

        assertThatThrownBy(() -> messageService.addMessage(
                new CreateMessageRequest(5L, 2L, null, MessageType.FILE, null, file, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getMessageByIdThrows404WhenMissing() {
        when(messageRepo.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessageById(7L)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void savedEntityMatchesRequest() {
        stubSave();
        messageService.addMessage(new CreateMessageRequest(1L, 2L, null, MessageType.TEXT, "hi", null, null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MessageType.TEXT);
    }

    private void assertBadRequest(CreateMessageRequest request) {
        assertThatThrownBy(() -> messageService.addMessage(request)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
