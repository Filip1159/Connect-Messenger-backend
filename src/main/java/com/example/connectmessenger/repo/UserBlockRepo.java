package com.example.connectmessenger.repo;

import com.example.connectmessenger.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepo extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerId(Long blockerId);

    List<UserBlock> findByBlockedId(Long blockedId);
}
