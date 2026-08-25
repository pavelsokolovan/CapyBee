package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import com.capybee.server.domain.checkin.CheckInEntry;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.CheckInEntryRepository;
import com.capybee.server.web.dto.CheckInResponse;
import com.capybee.server.web.dto.CreateCheckInRequest;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private CheckInEntryRepository checkInEntryRepository;

    @Mock
    private UserService userService;

    @Mock
    private OAuth2AuthenticationToken oauth2Token;

    @InjectMocks
    private CheckInService checkInService;

    private UserAccount testUser;
    private UUID testUserId;
    private UUID checkInId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        checkInId = UUID.randomUUID();

        testUser = new UserAccount();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setDisplayName("Test User");
        testUser.setGoogleSubject("google-123");
    }

    @Test
    void testCreateCheckInNewEntry() {
        // Arrange
        CreateCheckInRequest request = new CreateCheckInRequest(null, "happy", "Good day!");
        CheckInEntry savedEntry = new CheckInEntry();
        savedEntry.setId(checkInId);
        savedEntry.setUserAccount(testUser);
        savedEntry.setMood("happy");
        savedEntry.setNote("Good day!");
        savedEntry.setCreatedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.save(any(CheckInEntry.class))).thenReturn(savedEntry);

        // Act
        CheckInResponse response = checkInService.createCheckIn(oauth2Token, request);

        // Assert
        assertNotNull(response);
        assertEquals(checkInId, response.id());
        assertEquals("happy", response.mood());
        assertEquals("Good day!", response.note());
        verify(userService, times(1)).getCurrentUser(oauth2Token);
        verify(checkInEntryRepository, times(1)).save(any(CheckInEntry.class));
    }

    @Test
    void testCreateCheckInWithExistingId() {
        // Arrange
        CreateCheckInRequest request = new CreateCheckInRequest(checkInId, "sad", "Not feeling great");
        
        CheckInEntry existingEntry = new CheckInEntry();
        existingEntry.setId(checkInId);
        existingEntry.setUserAccount(testUser);
        existingEntry.setMood("sad");
        existingEntry.setNote("Not feeling great");
        existingEntry.setCreatedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findById(checkInId)).thenReturn(Optional.of(existingEntry));

        // Act
        CheckInResponse response = checkInService.createCheckIn(oauth2Token, request);

        // Assert
        assertNotNull(response);
        assertEquals(checkInId, response.id());
        assertEquals("sad", response.mood());
        verify(checkInEntryRepository, never()).save(any(CheckInEntry.class));
    }

    @Test
    void testCreateCheckInWithExistingIdFromAnotherUser() {
        // Arrange
        CreateCheckInRequest request = new CreateCheckInRequest(checkInId, "happy", "Good day!");
        
        UserAccount otherUser = new UserAccount();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");
        otherUser.setDisplayName("Other User");
        
        CheckInEntry existingEntry = new CheckInEntry();
        existingEntry.setId(checkInId);
        existingEntry.setUserAccount(otherUser);
        existingEntry.setMood("happy");
        existingEntry.setCreatedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findById(checkInId)).thenReturn(Optional.of(existingEntry));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkInService.createCheckIn(oauth2Token, request));
        assertEquals("Id already used by another account", exception.getMessage());
        verify(checkInEntryRepository, never()).save(any(CheckInEntry.class));
    }

    @Test
    void testGetMyRecentCheckIns() {
        // Arrange
        CheckInEntry entry1 = new CheckInEntry();
        entry1.setId(UUID.randomUUID());
        entry1.setUserAccount(testUser);
        entry1.setMood("happy");
        entry1.setNote("First day");
        entry1.setCreatedAt(Instant.now().minusSeconds(3600));

        CheckInEntry entry2 = new CheckInEntry();
        entry2.setId(UUID.randomUUID());
        entry2.setUserAccount(testUser);
        entry2.setMood("neutral");
        entry2.setNote("Second day");
        entry2.setCreatedAt(Instant.now());

        List<CheckInEntry> entries = List.of(entry1, entry2);

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findAllByUserAccount_IdOrderByCreatedAtAsc(testUserId))
                .thenReturn(entries);

        // Act
        List<CheckInResponse> responses = checkInService.getMyRecentCheckIns(oauth2Token);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("happy", responses.get(0).mood());
        assertEquals("neutral", responses.get(1).mood());
        verify(userService, times(1)).getCurrentUser(oauth2Token);
        verify(checkInEntryRepository, times(1))
                .findAllByUserAccount_IdOrderByCreatedAtAsc(testUserId);
    }

    @Test
    void testGetMyRecentCheckInsEmpty() {
        // Arrange
        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findAllByUserAccount_IdOrderByCreatedAtAsc(testUserId))
                .thenReturn(List.of());

        // Act
        List<CheckInResponse> responses = checkInService.getMyRecentCheckIns(oauth2Token);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void testDeleteCheckInSuccess() {
        // Arrange
        CheckInEntry entry = new CheckInEntry();
        entry.setId(checkInId);
        entry.setUserAccount(testUser);
        entry.setMood("happy");
        entry.setCreatedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findById(checkInId)).thenReturn(Optional.of(entry));

        // Act
        assertDoesNotThrow(() -> checkInService.deleteCheckIn(oauth2Token, checkInId));

        // Assert
        verify(checkInEntryRepository, times(1)).delete(entry);
    }

    @Test
    void testDeleteCheckInNotFound() {
        // Arrange
        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findById(checkInId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkInService.deleteCheckIn(oauth2Token, checkInId));
        assertEquals("Check-in not found", exception.getMessage());
        verify(checkInEntryRepository, never()).delete(any(CheckInEntry.class));
    }

    @Test
    void testDeleteCheckInUnauthorized() {
        // Arrange
        UserAccount otherUser = new UserAccount();
        otherUser.setId(UUID.randomUUID());
        
        CheckInEntry entry = new CheckInEntry();
        entry.setId(checkInId);
        entry.setUserAccount(otherUser);
        entry.setMood("happy");
        entry.setCreatedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(checkInEntryRepository.findById(checkInId)).thenReturn(Optional.of(entry));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> checkInService.deleteCheckIn(oauth2Token, checkInId));
        assertEquals("Not authorized to delete this check-in", exception.getMessage());
        verify(checkInEntryRepository, never()).delete(any(CheckInEntry.class));
    }
}
