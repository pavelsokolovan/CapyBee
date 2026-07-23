package com.capybee.server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.mission.Mission;
import com.capybee.server.domain.mission.MissionChildState;
import com.capybee.server.domain.profile.FamilyProfile;

public interface MissionChildStateRepository extends JpaRepository<MissionChildState, UUID> {

    Optional<MissionChildState> findByProfileAndMission(FamilyProfile profile, Mission mission);

    Optional<MissionChildState> findByProfile_IdAndMission_Id(UUID profileId, UUID missionId);
}
