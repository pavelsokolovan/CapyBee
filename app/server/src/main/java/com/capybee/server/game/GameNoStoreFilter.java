package com.capybee.server.game;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GameNoStoreFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/game/")
                && !request.getRequestURI().startsWith(request.getContextPath() + "/api/games/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
