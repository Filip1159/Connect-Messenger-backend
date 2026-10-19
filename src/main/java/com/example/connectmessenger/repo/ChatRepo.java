package com.example.connectmessenger.repo;


import com.example.connectmessenger.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepo extends JpaRepository<Chat, Long> {
    List<Chat> findByUsers_Id(Long userId);
    boolean existsByIdAndUsers_Id(Long chatId, Long userId);
}
