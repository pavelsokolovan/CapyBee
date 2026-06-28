package com.capybee.server.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class WebConfig {

    @GetMapping("/")
    public ResponseEntity<String> serveIndex() throws IOException {
        Resource resource = new ClassPathResource("static/index.html");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(content);
    }
}
