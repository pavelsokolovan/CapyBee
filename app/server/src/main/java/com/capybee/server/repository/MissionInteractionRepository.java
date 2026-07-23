package com.capybee.server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.mission.MissionInteraction;

public interface MissionInteractionRepository extends JpaRepository<MissionInteraction, UUID> {

    Optional<MissionInteraction> findFirstByProfile_IdAndMission_IdAndActionOrderByCreatedAtDesc(
            UUID profileId, UUID missionId, String action);
}