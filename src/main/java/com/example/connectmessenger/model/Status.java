package com.example.connectmessenger.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Table(name = "status")
public class Status {
    @EmbeddedId
    private StatusEmbeddedId id;
    private Long messageId;  // last displayed message
    private LocalDateTime time;  // when last message was displayed first time

    /** Membership flags: a status row is also the membership of the user in the chat. */
    @ColumnDefault("false")
    private boolean groupAdmin;
    @ColumnDefault("false")
    private boolean archived;  // hidden from the main list, only for this user
    @ColumnDefault("false")
    private boolean muted;  // no notifications, only for this user
}
