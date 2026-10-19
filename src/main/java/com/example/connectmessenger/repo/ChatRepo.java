package com.example.connectmessenger.repo;


import com.example.connectmessenger.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepo extends JpaRepository<Chat, Long> {
    List<Chat> findByUsers_Id(Long userId);
    boolean existsByIdAndUsers_Id(Long chatId, Long userId);

    /** The 1:1 chat of two users, if they have one (group chats don't count). */
    @Query("select c from Chat c join c.users a join c.users b "
            + "where a.id = :userId and b.id = :otherId and c.groupChat = false order by c.id")
    List<Chat> findDirectChats(@Param("userId") Long userId, @Param("otherId") Long otherId);

    @Query("select count(c) > 0 from Chat c join c.users a join c.users b where a.id = :userId and b.id = :otherId")
    boolean sharesChat(@Param("userId") Long userId, @Param("otherId") Long otherId);
}
