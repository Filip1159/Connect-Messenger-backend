package com.example.connectmessenger.service;

import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.Reaction;
import com.example.connectmessenger.model.dto.MessageDto;
import com.example.connectmessenger.model.dto.ReactionDto;
import com.example.connectmessenger.repo.ReactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReactionService {
    public static final Set<String> ALLOWED_EMOJIS = Set.of("👍", "❤️", "😂", "😮", "😢", "🙏");

    private final ReactionRepo reactionRepo;

    /** A user has one reaction per message: reacting again replaces the previous emoji. */
    @Transactional
    public void react(Message message, Long userId, String emoji) {
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot react to a deleted message");
        }
        if (emoji == null || !ALLOWED_EMOJIS.contains(emoji)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported emoji");
        }
        Reaction reaction = reactionRepo.findByMessageIdAndUserId(message.getId(), userId)
                .orElseGet(() -> Reaction.builder().messageId(message.getId()).userId(userId).build());
        if (!emoji.equals(reaction.getEmoji())) {
            reaction.setEmoji(emoji);
            reactionRepo.save(reaction);
        }
    }

    /** Idempotent. */
    @Transactional
    public void remove(Message message, Long userId) {
        reactionRepo.findByMessageIdAndUserId(message.getId(), userId).ifPresent(reactionRepo::delete);
    }

    public MessageDto toDto(Message message) {
        return toDtos(List.of(message)).getFirst();
    }

    /** One query for the reactions of all the messages. */
    public List<MessageDto> toDtos(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Reaction>> byMessage = reactionRepo.findByMessageIdIn(messages.stream().map(Message::getId).toList())
                .stream().collect(Collectors.groupingBy(Reaction::getMessageId));
        return messages.stream().map(m -> MessageDto.from(m, group(byMessage.getOrDefault(m.getId(), List.of())))).toList();
    }

    private static List<ReactionDto> group(List<Reaction> reactions) {
        Map<String, List<Long>> byEmoji = new LinkedHashMap<>();
        reactions.stream().sorted(java.util.Comparator.comparing(Reaction::getId, java.util.Comparator.nullsLast(Long::compare)))
                .forEach(r -> byEmoji.computeIfAbsent(r.getEmoji(), e -> new ArrayList<>()).add(r.getUserId()));
        return byEmoji.entrySet().stream().map(e -> new ReactionDto(e.getKey(), e.getValue())).toList();
    }
}
