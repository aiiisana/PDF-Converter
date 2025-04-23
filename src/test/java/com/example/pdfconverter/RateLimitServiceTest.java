package com.example.pdfconverter;

import com.example.pdfconverter.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
    }

    @Test
    void testFreeUserAllowedOnce() {
        String userId = "user1";
        String subscription = "free";
        long fileSize = 1024 * 1024;
        String fileHash = "hash1";

        assertTrue(service.isAllowed(userId, subscription, fileSize, fileHash));

        assertFalse(service.isAllowed(userId, subscription, fileSize, fileHash));
    }

    @Test
    void testFileSizeLimitExceeded() {
        String userId = "user2";
        String subscription = "pro";
        long bigFile = 6 * 1024 * 1024;
        String fileHash = "hash2";

        assertFalse(service.isAllowed(userId, subscription, bigFile, fileHash));
    }

    @Test
    void testFreezeAfterFailedAttempts() {
        String userId = "user3";
        String subscription = "free";
        long fileSize = 1024;
        String fileHash = "hash3";

        for (int i = 0; i < 3; i++) {
            service.isAllowed(userId, subscription, fileSize, fileHash);
        }

        assertFalse(service.isAllowed(userId, subscription, fileSize, fileHash));
    }

    @Test
    void testServerOverloadBlocksRequest() {
        String userId = "user4";
        String subscription = "pro";
        long fileSize = 1024;
        String fileHash = "hash4";

        service.updateServerLoad(RateLimitService.ServerLoad.HIGH);

        assertFalse(service.isAllowed(userId, subscription, fileSize, fileHash));
    }
}