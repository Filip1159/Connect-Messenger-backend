package com.example.connectmessenger.repo;

import com.example.connectmessenger.model.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepo extends JpaRepository<Reaction, Long> {
    List<Reaction> findByMessageIdIn(Collection<Long> messageIds);

    Optional<Reaction> findByMessageIdAndUserId(Long messageId, Long userId);
}
