package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import com.capybee.server.domain.game.GameResult;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.GameResultRepository;
import com.capybee.server.web.dto.CreateGameResultRequest;
import com.capybee.server.web.dto.GameResultResponse;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameResultRepository gameResultRepository;

    @Mock
    private UserService userService;

    @Mock
    private OAuth2AuthenticationToken oauth2Token;

    @InjectMocks
    private GameService gameService;

    private UserAccount testUser;
    private UUID testUserId;
    private UUID gameResultId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        gameResultId = UUID.randomUUID();

        testUser = new UserAccount();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setDisplayName("Test User");
        testUser.setGoogleSubject("google-123");
    }

    @Test
    void testCreateGameResultSuccess() {
        CreateGameResultRequest request = new CreateGameResultRequest(null, "pollen_match", 24000, Map.of("flips", 8, "pairsTotal", 4));

        GameResult saved = new GameResult();
        saved.setId(gameResultId);
        saved.setUserAccount(testUser);
        saved.setGameKey("pollen_match");
        saved.setDurationMs(24000);
        saved.setMetrics(Map.of("flips", 8, "pairsTotal", 4));
        saved.setCompletedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(gameResultRepository.save(any(GameResult.class))).thenReturn(saved);

        GameResultResponse response = gameService.createGameResult(oauth2Token, request);

        assertNotNull(response);
        assertEquals(gameResultId, response.id());
        assertEquals("pollen_match", response.gameKey());
        assertEquals(24000, response.durationMs());
        assertEquals(8, ((Number) response.metrics().get("flips")).intValue());
        verify(gameResultRepository, times(1)).save(any(GameResult.class));
    }

    @Test
    void testCreateGameResultWithExistingIdReturnsExistingEntry() {
        CreateGameResultRequest request = new CreateGameResultRequest(gameResultId, "pollen_match", 30000, Map.of("flips", 10, "pairsTotal", 4));

        GameResult existing = new GameResult();
        existing.setId(gameResultId);
        existing.setUserAccount(testUser);
        existing.setGameKey("pollen_match");
        existing.setDurationMs(30000);
        existing.setMetrics(Map.of("flips", 10, "pairsTotal", 4));
        existing.setCompletedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(gameResultRepository.findById(gameResultId)).thenReturn(Optional.of(existing));

        GameResultResponse response = gameService.createGameResult(oauth2Token, request);

        assertNotNull(response);
        assertEquals(gameResultId, response.id());
        verify(gameResultRepository, never()).save(any(GameResult.class));
    }

    @Test
    void testCreateGameResultWithExistingIdFromAnotherUserRejected() {
        CreateGameResultRequest request = new CreateGameResultRequest(gameResultId, "pollen_match", 30000, Map.of("flips", 10, "pairsTotal", 4));

        UserAccount otherUser = new UserAccount();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");
        otherUser.setDisplayName("Other User");

        GameResult existing = new GameResult();
        existing.setId(gameResultId);
        existing.setUserAccount(otherUser);
        existing.setGameKey("pollen_match");
        existing.setDurationMs(30000);
        existing.setMetrics(Map.of("flips", 10, "pairsTotal", 4));
        existing.setCompletedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(gameResultRepository.findById(gameResultId)).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> gameService.createGameResult(oauth2Token, request));

        assertEquals("Id already used by another account", exception.getMessage());
        verify(gameResultRepository, never()).save(any(GameResult.class));
    }

    @Test
    void testCreateGameResultRejectsInvalidDurationsAndMetrics() {
        assertThrows(IllegalArgumentException.class,
            () -> gameService.createGameResult(oauth2Token, new CreateGameResultRequest(null, "pollen_match", -1, Map.of("flips", 8, "pairsTotal", 4))));

        assertThrows(IllegalArgumentException.class,
            () -> gameService.createGameResult(oauth2Token, new CreateGameResultRequest(null, "pollen_match", 10, Map.of("flips", -1, "pairsTotal", 4))));

        assertThrows(IllegalArgumentException.class,
            () -> gameService.createGameResult(oauth2Token, new CreateGameResultRequest(null, "pollen_match", 10, Map.of("flips", 8, "pairsTotal", -1))));
    }

    @Test
    void testGetMyGameResultsReturnsRowsForCurrentUser() {
        GameResult result = new GameResult();
        result.setId(gameResultId);
        result.setUserAccount(testUser);
        result.setGameKey("pollen_match");
        result.setDurationMs(18000);
        result.setMetrics(Map.of("flips", 6, "pairsTotal", 3));
        result.setCompletedAt(Instant.now());

        when(userService.getCurrentUser(oauth2Token)).thenReturn(testUser);
        when(gameResultRepository.findAllByUserAccount_IdAndGameKeyOrderByCompletedAtDesc(testUserId, "pollen_match"))
                .thenReturn(List.of(result));

        List<GameResultResponse> responses = gameService.getMyGameResults(oauth2Token, "pollen_match", 20);

        assertEquals(1, responses.size());
        assertEquals("pollen_match", responses.get(0).gameKey());
        verify(gameResultRepository, times(1)).findAllByUserAccount_IdAndGameKeyOrderByCompletedAtDesc(testUserId, "pollen_match");
    }
}
