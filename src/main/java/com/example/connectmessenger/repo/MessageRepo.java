package com.example.connectmessenger.repo;


import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepo extends JpaRepository<Message, Long> {
    Optional<Message> findByTypeAndContent(MessageType type, String content);

    /** Messages written by others after `after` (the reader's last seen message). */
    long countByChatIdAndIdGreaterThanAndUserIdNot(Long chatId, Long after, Long userId);

    /** Oldest first; used to catch up after a reconnect. */
    List<Message> findByChatIdAndIdGreaterThanOrderByIdAsc(Long chatId, Long after, Limit limit);

    /** Newest first; pass a message id as `before` to get the page of older messages. */
    List<Message> findByChatIdAndIdLessThanOrderByIdDesc(Long chatId, Long before, Limit limit);

    /** Newest first; text messages that contain `text` (case-insensitive, wildcards are escaped by Spring Data). */
    List<Message> findByChatIdAndTypeAndDeletedAtIsNullAndContentContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
            Long chatId, MessageType type, String text, Long before, Limit limit);
}
