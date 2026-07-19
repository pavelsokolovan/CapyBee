package com.capybee.server.service;

import java.util.List;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capybee.server.domain.checkin.CheckInEntry;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.CheckInEntryRepository;
import com.capybee.server.web.dto.CheckInResponse;
import com.capybee.server.web.dto.CreateCheckInRequest;

@Service
public class CheckInService {

    private final CheckInEntryRepository checkInEntryRepository;
    private final UserService userService;

    public CheckInService(CheckInEntryRepository checkInEntryRepository, UserService userService) {
        this.checkInEntryRepository = checkInEntryRepository;
        this.userService = userService;
    }

    @Transactional
    public CheckInResponse createCheckIn(OAuth2AuthenticationToken oauth2Token, CreateCheckInRequest request) {
        UserAccount user = userService.getCurrentUser(oauth2Token);

        CheckInEntry entry = new CheckInEntry();
        entry.setUserAccount(user);
        entry.setMood(request.mood());
        entry.setNote(request.note());

        CheckInEntry saved = checkInEntryRepository.save(entry);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> getMyRecentCheckIns(OAuth2AuthenticationToken oauth2Token) {
        UserAccount user = userService.getCurrentUser(oauth2Token);
        return checkInEntryRepository.findAllByUserAccount_IdOrderByCreatedAtAsc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CheckInResponse toResponse(CheckInEntry entry) {
        return new CheckInResponse(entry.getId(), entry.getMood(), entry.getNote(), entry.getCreatedAt());
    }
}
