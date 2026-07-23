package com.capybee.server.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.capybee.server.domain.mission.Mission;

public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findTop20ByActiveOrderByCreatedAtDesc(boolean active);

    List<Mission> findTop20ByOrderByCreatedAtDesc();

    @Query("select m from Mission m where m.active = :active")
    List<Mission> findActiveMissionsForProfileOrderByLastActionedAt(@Param("profileId") UUID profileId,
            @Param("active") boolean active);
}
