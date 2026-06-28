package com.capybee.server.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.memory.MemoryEntry;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, UUID> {

    List<MemoryEntry> findTop20ByProfile_IdOrderByCreatedAtDesc(UUID profileId);

    List<MemoryEntry> findTop20ByProfile_IdAndWorldTypeOrderByCreatedAtDesc(UUID profileId, String worldType);

    Optional<MemoryEntry> findByIdAndProfile_Id(UUID id, UUID profileId);
}
