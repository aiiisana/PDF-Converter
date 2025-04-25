package com.example.pdfconverter.service;

import com.example.pdfconverter.model.RateLimitResult;
import io.github.bucket4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitService {


    public static final int FREE_LIMIT = 2;
    public static final int PRO_LIMIT = 10;
    public static final int VIP_LIMIT = 200;
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    public static final int FILE_GENERATION_LIMIT = 5;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration FREEZE_DURATION = Duration.ofMinutes(30);
    public static final Duration RESET_PERIOD = Duration.ofHours(1);
    public static final Duration FILE_ATTEMPT_WINDOW = Duration.ofMinutes(5);

    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> userAttempts = new ConcurrentHashMap<>();
    private final Map<String, FileGenerationTracker> fileGenerations = new ConcurrentHashMap<>();
    private final Map<String, FileAttemptTracker> fileAttemptTrackers = new ConcurrentHashMap<>();

    public RateLimitResult isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        log.debug("Checking limits for user={}, fileHash={}", userId, fileHash);

        // 1. Check file size limit
        if (fileSize > MAX_FILE_SIZE) {
            log.warn("File size limit exceeded for user={}", userId);
            return new RateLimitResult(false, "File size exceeds the maximum allowed size.");
        }

        // 2. Check file generation limit
        FileGenerationTracker generationTracker = fileGenerations.computeIfAbsent(fileHash,
                k -> new FileGenerationTracker());
        int currentGenerations = generationTracker.getGenerations();
        if (currentGenerations >= FILE_GENERATION_LIMIT) {
            log.warn("File generation limit reached for user={}, fileHash={}, generations={}",
                    userId, fileHash, currentGenerations);
            return new RateLimitResult(false, "This file has been generated too many times.");
        }

        // 3. Check if user is frozen
        AttemptInfo attemptInfo = userAttempts.get(userId);
        if (attemptInfo != null && attemptInfo.isFrozen()) {
            long minutesLeft = Duration.between(Instant.now(), attemptInfo.getFreezeUntil()).toMinutes();
            return new RateLimitResult(false, "Too many failed attempts. Try again in " + minutesLeft + " minutes.");
        }

        // 4. Check consecutive attempts for this file
        String fileAttemptKey = userId + "|" + fileHash;
        FileAttemptTracker attemptTracker = fileAttemptTrackers.computeIfAbsent(fileAttemptKey,
                k -> new FileAttemptTracker());

        attemptTracker.increment();

        if (attemptTracker.getAttempts() >= MAX_ATTEMPTS) {
            incrementAttempts(userId);
            return new RateLimitResult(false, "Too many attempts with this file. Try a different file.");
        }

        // 5. Check rate limit bucket
        Bucket bucket = resolveBucket(userId, subscriptionType);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            incrementAttempts(userId);
            attemptTracker.increment();
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            return new RateLimitResult(false, "Rate limit exceeded. Try again in " + waitSeconds + " seconds.");
        }

        // Success - update trackers
        resetAttempts(userId);
        attemptTracker.reset();
        generationTracker.increment();
        return new RateLimitResult(true, "Allowed");
    }

    private Bucket resolveBucket(String userId, String subscriptionType) {
        int limit = switch (subscriptionType.toLowerCase()) {
            case "pro" -> PRO_LIMIT;
            case "vip" -> VIP_LIMIT;
            default -> FREE_LIMIT;
        };

        return userBuckets.computeIfAbsent(userId, key -> {
            Bandwidth bandwidth = Bandwidth.classic(limit, Refill.intervally(limit, RESET_PERIOD));
            return Bucket4j.builder().addLimit(bandwidth).build();
        });
    }

    private void incrementAttempts(String userId) {
        userAttempts.compute(userId, (key, info) -> {
            if (info == null) {
                return new AttemptInfo(1);
            }
            info.incrementAttempts();
            return info;
        });
    }

    private void resetAttempts(String userId) {
        userAttempts.remove(userId);
    }

    private static class FileAttemptTracker {
        private int attempts;
        private Instant lastAttemptTime = Instant.now();

        public synchronized void increment() {
            if (Duration.between(lastAttemptTime, Instant.now()).compareTo(FILE_ATTEMPT_WINDOW) > 0) {
                attempts = 0;
            }
            attempts++;
            lastAttemptTime = Instant.now();
        }

        public synchronized int getAttempts() {
            if (Duration.between(lastAttemptTime, Instant.now()).compareTo(FILE_ATTEMPT_WINDOW) > 0) {
                attempts = 0;
            }
            return attempts;
        }

        public synchronized void reset() {
            attempts = 0;
            lastAttemptTime = Instant.now();
        }
    }

    private static class FileGenerationTracker {
        private int generations;
        private Instant firstGenerationTime = Instant.now();

        public synchronized void increment() {
            // Reset counter if first generation was more than 24 hours ago
            if (Duration.between(firstGenerationTime, Instant.now()).toHours() >= 24) {
                generations = 0;
                firstGenerationTime = Instant.now();
            }
            generations++;
        }

        public synchronized int getGenerations() {
            // Reset counter if first generation was more than 24 hours ago
            if (Duration.between(firstGenerationTime, Instant.now()).toHours() >= 24) {
                generations = 0;
                firstGenerationTime = Instant.now();
            }
            return generations;
        }
    }
}