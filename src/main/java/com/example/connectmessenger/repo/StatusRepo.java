package com.example.connectmessenger.repo;


import com.example.connectmessenger.model.Status;
import com.example.connectmessenger.model.StatusEmbeddedId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusRepo extends JpaRepository<Status, StatusEmbeddedId> {
    /** The members of a chat (one status row per member). */
    List<Status> findByIdChatId(Long chatId);
}
