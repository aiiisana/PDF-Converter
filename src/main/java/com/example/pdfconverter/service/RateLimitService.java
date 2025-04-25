package com.example.pdfconverter.service;

import io.github.bucket4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    public static final int FREE_LIMIT = 10;
    public static final int PRO_LIMIT = 50;
    public static final int VIP_LIMIT = 200;
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    public static final int FILE_GENERATION_LIMIT = 5;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration FREEZE_DURATION = Duration.ofMinutes(30);
    public static final Duration RESET_PERIOD = Duration.ofHours(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    private final Map<String, Integer> fileGenerations = new ConcurrentHashMap<>();

    public boolean isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        System.out.println("Checking rate limit for userId: " + userId);
        if (fileSize > MAX_FILE_SIZE) {
            System.out.println("File size too large: " + fileSize);
            return false;
        }

        int generations = fileGenerations.getOrDefault(fileHash, 0);
        if (generations >= FILE_GENERATION_LIMIT) {
            System.out.println("File generation limit reached for fileHash: " + fileHash);
            return false;
        }

        AttemptInfo attemptInfo = attempts.get(userId);
        if (attemptInfo != null && attemptInfo.isFrozen()) {
            System.out.println("User " + userId + " is frozen due to failed attempts.");
            return false;
        }

        Bucket bucket = resolveBucket(userId, subscriptionType);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            System.out.println("Rate limit exceeded for userId: " + userId);
            incrementAttempts(userId);
        } else {
            System.out.println("Rate limit allowed for userId: " + userId);
            resetAttempts(userId);
            fileGenerations.merge(fileHash, 1, Integer::sum);
        }

        return allowed;
    }

    private Bucket resolveBucket(String userId, String subscriptionType) {
        int limit = switch (subscriptionType.toLowerCase()) {
            case "pro" -> PRO_LIMIT;
            case "vip" -> VIP_LIMIT;
            default -> FREE_LIMIT;
        };

        return buckets.computeIfAbsent(userId, key -> {
            Bandwidth bandwidth = Bandwidth.classic(limit, Refill.intervally(limit, RESET_PERIOD));
            return Bucket4j.builder().addLimit(bandwidth).build();
        });
    }

    private void incrementAttempts(String userId) {
        attempts.compute(userId, (key, info) -> {
            if (info == null) {
                return new AttemptInfo(1);
            } else {
                info.incrementAttempts();
                return info;
            }
        });
    }

    private void resetAttempts(String userId) {
        attempts.remove(userId);
    }
}