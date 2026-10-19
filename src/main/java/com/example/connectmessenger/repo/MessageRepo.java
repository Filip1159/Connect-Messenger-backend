package com.example.connectmessenger.repo;


import com.example.connectmessenger.model.Message;
import com.example.connectmessenger.model.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepo extends JpaRepository<Message, Long> {
    Optional<Message> findByTypeAndContent(MessageType type, String content);
}
