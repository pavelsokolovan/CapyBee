package com.capybee.server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.profile.FamilyProfile;

public interface FamilyProfileRepository extends JpaRepository<FamilyProfile, UUID> {

    Optional<FamilyProfile> findByParentUser_Id(UUID parentUserId);
}
