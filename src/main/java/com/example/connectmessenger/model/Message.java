package com.example.connectmessenger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long chatId;
    private Long userId;

    private LocalDateTime time;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    @Column(length = 1000)
    private String content;

    /** Set when the author changed the text. */
    private LocalDateTime editedAt;

    /** Soft delete: the row stays (ids, unread counts), the content is no longer served. */
    private LocalDateTime deletedAt;

    /** The message this one replies to (in the same chat). */
    private Long replyToId;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
