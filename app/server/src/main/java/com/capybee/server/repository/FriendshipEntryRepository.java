package com.capybee.server.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.friendship.FriendshipEntry;

public interface FriendshipEntryRepository extends JpaRepository<FriendshipEntry, UUID> {

    List<FriendshipEntry> findTop20ByProfile_IdOrderByCreatedAtDesc(UUID profileId);

    Optional<FriendshipEntry> findByIdAndProfile_Id(UUID id, UUID profileId);
}
