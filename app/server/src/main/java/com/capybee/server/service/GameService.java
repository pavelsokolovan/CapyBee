package com.capybee.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capybee.server.domain.game.GameResult;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.GameResultRepository;
import com.capybee.server.web.dto.CreateGameResultRequest;
import com.capybee.server.web.dto.GameResultResponse;

@Service
public class GameService {

    private final GameResultRepository gameResultRepository;
    private final UserService userService;

    public GameService(GameResultRepository gameResultRepository, UserService userService) {
        this.gameResultRepository = gameResultRepository;
        this.userService = userService;
    }

    @Transactional
    public GameResultResponse createGameResult(OAuth2AuthenticationToken oauth2Token, CreateGameResultRequest request) {
        UserAccount user = userService.getCurrentUser(oauth2Token);

        if (request.gameKey() == null || request.gameKey().isBlank()) {
            throw new IllegalArgumentException("Game key is required");
        }
        if (request.durationMs() == null || request.durationMs() < 0) {
            throw new IllegalArgumentException("Duration must be non-negative");
        }
        if (request.metrics() != null) {
            validateMetrics(request.metrics());
        }

        if (request.id() != null) {
            Optional<GameResult> existing = gameResultRepository.findById(request.id());
            if (existing.isPresent()) {
                GameResult found = existing.get();
                if (!found.getUserAccount().getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Id already used by another account");
                }
                return toResponse(found);
            }
        }

        GameResult result = new GameResult();
        if (request.id() != null) {
            result.setId(request.id());
        }
        result.setUserAccount(user);
        result.setGameKey(request.gameKey());
        result.setDurationMs(request.durationMs());
        result.setMetrics(request.metrics() == null ? new HashMap<>() : request.metrics());

        GameResult saved = gameResultRepository.save(result);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GameResultResponse> getMyGameResults(OAuth2AuthenticationToken oauth2Token, String gameKey, int limit) {
        UserAccount user = userService.getCurrentUser(oauth2Token);

        List<GameResult> results = (gameKey == null || gameKey.isBlank())
                ? gameResultRepository.findAllByUserAccount_IdOrderByCompletedAtDesc(user.getId())
                : gameResultRepository.findAllByUserAccount_IdAndGameKeyOrderByCompletedAtDesc(user.getId(), gameKey);

        return results.stream().limit(Math.max(0, limit)).map(this::toResponse).toList();
    }

    private void validateMetrics(Map<String, Object> metrics) {
        Map<String, Object> normalized = new HashMap<>(metrics);

        if (normalized.containsKey("flips")) {
            Number flips = asNumber(normalized.get("flips"));
            if (flips == null || flips.intValue() < 0) {
                throw new IllegalArgumentException("Flips must be non-negative");
            }
        }

        if (normalized.containsKey("pairsTotal")) {
            Number pairsTotal = asNumber(normalized.get("pairsTotal"));
            if (pairsTotal == null || pairsTotal.intValue() < 0) {
                throw new IllegalArgumentException("Pairs total must be non-negative");
            }
        }
    }

    private Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private GameResultResponse toResponse(GameResult entry) {
        return new GameResultResponse(
                entry.getId(),
                entry.getGameKey(),
                entry.getDurationMs(),
                entry.getMetrics() == null ? Map.of() : new HashMap<>(entry.getMetrics()),
                entry.getCompletedAt());
    }
}
