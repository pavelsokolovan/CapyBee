package com.capybee.server.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.checkin.CheckInEntry;

public interface CheckInEntryRepository extends JpaRepository<CheckInEntry, UUID> {

    List<CheckInEntry> findTop10ByUserAccount_IdOrderByCreatedAtDesc(UUID userAccountId);
}
