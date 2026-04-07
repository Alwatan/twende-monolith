package com.twende.gateway.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @GetMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallbackGet() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildResponse());
    }

    @PostMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallbackPost() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(buildResponse());
    }

    private Map<String, Object> buildResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Service temporarily unavailable. Please try again shortly.");
        response.put("data", null);
        return response;
    }
}
