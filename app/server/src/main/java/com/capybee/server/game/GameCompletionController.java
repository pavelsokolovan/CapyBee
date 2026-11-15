package com.capybee.server.game;

import com.capybee.server.service.UserService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/game")
public class GameCompletionController {
    private final GameCompletionService service;
    private final UserService users;
    public GameCompletionController(GameCompletionService service, UserService users) {this.service=service;this.users=users;}
    @GetMapping("/runs/{runId}/completion")
    public org.springframework.http.ResponseEntity<?> get(OAuth2AuthenticationToken auth, @PathVariable UUID runId,
            Locale locale) {
        var value=service.get(users.getCurrentUser(auth).getId(),runId,"pl".equals(locale.getLanguage()));
        return value==null ? org.springframework.http.ResponseEntity.accepted().body(java.util.Map.of("status","PROCESSING","retryAfterMs",1000))
                : org.springframework.http.ResponseEntity.ok(value);
    }
    @PostMapping("/runs/{runId}/completion/{kind}")
    public GameCompletionService.CompletionDto accept(OAuth2AuthenticationToken auth, @PathVariable UUID runId,
            @PathVariable String kind, @RequestBody GameCompletionService.AcceptBody body,
            Locale locale) {
        if (!kind.equals("mission") && !kind.equals("cell")) throw new IllegalArgumentException("Invalid action");
        return service.accept(users.getCurrentUser(auth).getId(),runId,body,kind.equals("mission"),"pl".equals(locale.getLanguage()));
    }
    @GetMapping("/mission-selections")
    public java.util.List<UUID> selected(OAuth2AuthenticationToken auth) {
        return service.selectedMissions(users.getCurrentUser(auth).getId());
    }
    @DeleteMapping("/runs/{runId}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(OAuth2AuthenticationToken auth,@PathVariable UUID runId) {
        service.archive(users.getCurrentUser(auth).getId(),runId);
    }
}
