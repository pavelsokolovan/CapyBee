package com.capybee.server.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.game.GameResult;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {

    List<GameResult> findAllByUserAccount_IdOrderByCompletedAtDesc(UUID userAccountId);

    List<GameResult> findAllByUserAccount_IdAndGameKeyOrderByCompletedAtDesc(UUID userAccountId, String gameKey);
}
