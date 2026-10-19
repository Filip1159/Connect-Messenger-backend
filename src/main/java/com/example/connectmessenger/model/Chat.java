package com.example.connectmessenger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;


@Entity
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chats")
public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Title of a group chat; 1:1 chats have none (the other person is the title). */
    @Column(length = 60)
    private String name;

    /** Group chats can change their members; a 1:1 chat always has the same two. */
    @ColumnDefault("false")  // existing rows and the seed data are 1:1 chats
    private boolean groupChat;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "chatId", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Message> messages;

    @ManyToMany(mappedBy = "chats")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<User> users;

    @OneToMany
    @JoinColumn(name = "chatId", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Status> status;
}
