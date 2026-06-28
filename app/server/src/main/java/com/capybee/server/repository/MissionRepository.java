package com.capybee.server.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.mission.Mission;

public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findTop20ByActiveOrderByCreatedAtDesc(boolean active);

    List<Mission> findTop20ByOrderByCreatedAtDesc();
}
