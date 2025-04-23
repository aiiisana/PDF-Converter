package com.example.pdfconverter.controller;

import com.example.pdfconverter.service.RateLimitService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<String> convert(@RequestBody ConvertRequest request) {
        boolean allowed = rateLimitService.isAllowed(
                request.getUserId(),
                request.getSubscription(),
                request.getFileSize(),
                request.getFileHash()
        );

        if (allowed) {
            return ResponseEntity.ok("Request allowed");
        } else {
            return ResponseEntity.status(429).body("Rate limit exceeded");
        }
    }

    @Data
    @AllArgsConstructor
    static class ConvertRequest {
        private String userId;
        private String subscription;
        private long fileSize;
        private String fileHash;
    }
}