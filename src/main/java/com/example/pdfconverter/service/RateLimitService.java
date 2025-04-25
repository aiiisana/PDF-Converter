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
    // Configuration constants
    public static final int FREE_LIMIT = 2;
    public static final int PRO_LIMIT = 10;
    public static final int VIP_LIMIT = 200;
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    public static final int FILE_GENERATION_LIMIT = 5;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration FREEZE_DURATION = Duration.ofMinutes(30);
    public static final Duration RESET_PERIOD = Duration.ofHours(1);
    public static final Duration FILE_ATTEMPT_WINDOW = Duration.ofMinutes(5);

    private static final long SMALL_FILE_THRESHOLD = 1024 * 1024; // 1 MB
    private static final long MEDIUM_FILE_THRESHOLD = 5 * 1024 * 1024; // 5 MB

    // Thread-safe storage
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> userAttempts = new ConcurrentHashMap<>();
    private final Map<String, FileGenerationTracker> fileGenerations = new ConcurrentHashMap<>();
    private final Map<String, FileAttemptTracker> fileAttemptTrackers = new ConcurrentHashMap<>();

    public RateLimitResult isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        // 1. Validate file size first (fastest check)
        if (fileSize > MAX_FILE_SIZE) {
            return handleSizeLimitExceeded(userId, fileHash);
        }

        // 2. Check user freeze status
        if (isUserFrozen(userId)) {
            return createFrozenResponse(userId);
        }

        // 3. Check file generation limit
        if (isFileGenerationLimitReached(fileHash)) {
            return handleGenerationLimit(userId, fileHash);
        }

        // 4. Check file-specific attempts
        FileAttemptTracker fileAttemptTracker = getFileAttemptTracker(userId, fileHash);
        if (fileAttemptTracker.isFrozen()) {
            return handleFileAttemptLimit(userId, fileHash);
        }

        // 5. Process rate limiting
        return processRateLimiting(userId, subscriptionType, fileSize, fileHash, fileAttemptTracker);
    }

    private RateLimitResult handleSizeLimitExceeded(String userId, String fileHash) {
        log.warn("File size limit exceeded for user={}", userId);
        incrementCounters(userId, fileHash);
        return new RateLimitResult(false, "File size exceeds the maximum allowed size.");
    }

    private boolean isUserFrozen(String userId) {
        AttemptInfo attemptInfo = userAttempts.get(userId);
        return attemptInfo != null && attemptInfo.isFrozen();
    }

    private RateLimitResult createFrozenResponse(String userId) {
        AttemptInfo attemptInfo = userAttempts.get(userId);
        long minutesLeft = Duration.between(Instant.now(), attemptInfo.getFreezeUntil()).toMinutes();
        log.warn("User frozen: user={}, minutesLeft={}", userId, minutesLeft);
        return new RateLimitResult(false, "Too many failed attempts. Try again in " + minutesLeft + " minutes.");
    }

    private boolean isFileGenerationLimitReached(String fileHash) {
        FileGenerationTracker tracker = fileGenerations.computeIfAbsent(fileHash, k -> new FileGenerationTracker());
        return tracker.getGenerations() >= FILE_GENERATION_LIMIT;
    }

    private RateLimitResult handleGenerationLimit(String userId, String fileHash) {
        incrementCounters(userId, fileHash);
        log.warn("File generation limit reached for user={}, fileHash={}", userId, fileHash);
        return new RateLimitResult(false, "This file has been generated too many times.");
    }

    private FileAttemptTracker getFileAttemptTracker(String userId, String fileHash) {
        String key = userId + "|" + fileHash;
        return fileAttemptTrackers.computeIfAbsent(key, k -> new FileAttemptTracker());
    }

    private RateLimitResult handleFileAttemptLimit(String userId, String fileHash) {
        incrementUserAttempts(userId);
        log.warn("File attempt limit reached for user={}, fileHash={}", userId, fileHash);
        return new RateLimitResult(false, "Too many attempts with this file. Try again later.");
    }

    private RateLimitResult processRateLimiting(String userId, String subscriptionType,
                                                long fileSize, String fileHash,
                                                FileAttemptTracker fileAttemptTracker) {
        int tokens = calculateTokens(fileSize);
        Bucket bucket = getOrCreateBucket(userId, subscriptionType);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokens);

        if (!probe.isConsumed()) {
            incrementCounters(userId, fileHash);
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("Rate limit exceeded: user={}, wait={}s", userId, waitSeconds);
            return new RateLimitResult(false, "Rate limit exceeded. Try again in " + waitSeconds + " seconds.");
        }

        // Successful request
        resetUserAttempts(userId);
        fileAttemptTracker.reset();
        incrementFileGenerations(fileHash);

        log.debug("Request allowed: user={}, fileHash={}", userId, fileHash);
        return new RateLimitResult(true, "Request allowed.");
    }

    private void incrementCounters(String userId, String fileHash) {
        incrementUserAttempts(userId);
        getFileAttemptTracker(userId, fileHash).increment();
    }

    private void incrementUserAttempts(String userId) {
        userAttempts.compute(userId, (key, info) -> {
            if (info == null) {
                info = new AttemptInfo(1);
            } else {
                info.incrementAttempts();
            }
            return info;
        });
    }

    private void resetUserAttempts(String userId) {
        userAttempts.remove(userId);
    }

    private void incrementFileGenerations(String fileHash) {
        fileGenerations.computeIfAbsent(fileHash, k -> new FileGenerationTracker()).increment();
    }

    private int calculateTokens(long fileSize) {
        if (fileSize < SMALL_FILE_THRESHOLD) return 1;
        if (fileSize < MEDIUM_FILE_THRESHOLD) return 3;
        return 5;
    }

    private Bucket getOrCreateBucket(String userId, String subscriptionType) {
        int limit = switch (subscriptionType.toLowerCase()) {
            case "pro" -> PRO_LIMIT;
            case "vip" -> VIP_LIMIT;
            default -> FREE_LIMIT;
        };

        return userBuckets.computeIfAbsent(userId, key ->
                Bucket4j.builder()
                        .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, RESET_PERIOD)))
                        .build()
        );
    }

    private static class FileAttemptTracker {
        private int attempts;
        private Instant lastAttemptTime = Instant.now();
        private Instant frozenUntil;

        public synchronized void increment() {
            if (Duration.between(lastAttemptTime, Instant.now()).compareTo(FILE_ATTEMPT_WINDOW) > 0) {
                attempts = 0;
            }
            attempts++;
            lastAttemptTime = Instant.now();

            if (attempts >= MAX_ATTEMPTS) {
                frozenUntil = Instant.now().plus(FILE_ATTEMPT_WINDOW);
            }
        }

        public synchronized int getAttempts() {
            if (Duration.between(lastAttemptTime, Instant.now()).compareTo(FILE_ATTEMPT_WINDOW) > 0) {
                attempts = 0;
            }
            return attempts;
        }

        public synchronized boolean isFrozen() {
            return frozenUntil != null && Instant.now().isBefore(frozenUntil);
        }

        public synchronized void reset() {
            attempts = 0;
            frozenUntil = null;
            lastAttemptTime = Instant.now();
        }
    }

    private static class FileGenerationTracker {
        private int generations;
        private Instant firstGenerationTime = Instant.now();

        public synchronized void increment() {
            if (Duration.between(firstGenerationTime, Instant.now()).toHours() >= 24) {
                generations = 0;
                firstGenerationTime = Instant.now();
            }
            generations++;
        }

        public synchronized int getGenerations() {
            if (Duration.between(firstGenerationTime, Instant.now()).toHours() >= 24) {
                generations = 0;
                firstGenerationTime = Instant.now();
            }
            return generations;
        }
    }
}