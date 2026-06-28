package com.capybee.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.mission.MissionCompletion;

public interface MissionCompletionRepository extends JpaRepository<MissionCompletion, UUID> {

    List<MissionCompletion> findTop20ByUserAccount_IdOrderByCompletedAtDesc(UUID userAccountId);

    List<MissionCompletion> findTop20ByUserAccount_IdAndCompletedAtLessThanOrderByCompletedAtDesc(UUID userAccountId,
            Instant before);
}
