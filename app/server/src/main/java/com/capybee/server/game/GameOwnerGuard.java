package com.capybee.server.game;

import com.capybee.server.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** An owner hint is never authorization. It only rejects a stale browser account. */
@Configuration
public class GameOwnerGuard implements WebMvcConfigurer {
    private final UserService users;
    public GameOwnerGuard(UserService users) { this.users = users; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String expected = request.getHeader("X-Game-Owner");
                if (expected != null && request.getUserPrincipal() instanceof OAuth2AuthenticationToken authentication) {
                    String actual = users.getCurrentUser(authentication).getId().toString();
                    if (!actual.equals(expected)) {
                        response.setStatus(409);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":{\"code\":\"GAME_OWNER_CHANGED\",\"message\":\"Sign in again.\",\"details\":{}}}");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/game/**", "/api/games/**");
    }
}
