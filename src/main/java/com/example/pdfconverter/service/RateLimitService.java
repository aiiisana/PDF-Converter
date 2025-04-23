package com.example.pdfconverter.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private static final int FREE_LIMIT = 1;
    private static final int PRO_LIMIT = 15;
    private static final int VIP_LIMIT = 50;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int FILE_GENERATION_LIMIT = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration FREEZE_DURATION = Duration.ofMinutes(30);

    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, AttemptInfo> attemptCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> fileGenerationCache = new ConcurrentHashMap<>();

    private ServerLoad serverLoad = ServerLoad.NORMAL;

    public boolean isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        if (fileSize > MAX_FILE_SIZE) {
            return false;
        }

        int generations = fileGenerationCache.getOrDefault(fileHash, 0);
        if (generations >= FILE_GENERATION_LIMIT) {
            return false;
        }

        AttemptInfo attemptInfo = attemptCache.get(userId);
        if (attemptInfo != null && attemptInfo.isFrozen()) {
            return false;
        }

        Bucket bucket = userBuckets.computeIfAbsent(userId, k -> createBucketForSubscription(subscriptionType));

        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            incrementAttempts(userId);
        } else {
            resetAttempts(userId);
            fileGenerationCache.merge(fileHash, 1, Integer::sum);
        }

        return allowed && !isServerOverloaded();
    }

    private Bucket createBucketForSubscription(String subscriptionType) {
        int limit;
        switch (subscriptionType.toLowerCase()) {
            case "pro":
                limit = PRO_LIMIT;
                break;
            case "vip":
                limit = VIP_LIMIT;
                break;
            default: // free
                limit = FREE_LIMIT;
        }

        long tokensPerSecond = (long) Math.ceil((double) limit / 86400);

        LocalBucketBuilder builder = Bucket4j.builder();

        builder.addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofDays(1))));

        if (serverLoad == ServerLoad.HIGH) {
            builder.addLimit(Bandwidth.simple(limit / 2, Duration.ofMinutes(1)));
        }

        return builder.build();
    }

    private void incrementAttempts(String userId) {
        attemptCache.compute(userId, (k, v) -> {
            if (v == null) {
                return new AttemptInfo(1);
            }
            v.incrementAttempts();
            return v;
        });
    }

    private void resetAttempts(String userId) {
        attemptCache.remove(userId);
    }

    private boolean isServerOverloaded() {
        return serverLoad == ServerLoad.HIGH;
    }

    public void updateServerLoad(ServerLoad load) {
        this.serverLoad = load;
    }

    private static class AttemptInfo {
        private int attempts;
        private long freezeTime;

        AttemptInfo(int attempts) {
            this.attempts = attempts;
            if (attempts >= MAX_ATTEMPTS) {
                this.freezeTime = System.currentTimeMillis() + FREEZE_DURATION.toMillis();
            }
        }

        void incrementAttempts() {
            this.attempts++;
            if (this.attempts >= MAX_ATTEMPTS) {
                this.freezeTime = System.currentTimeMillis() + FREEZE_DURATION.toMillis();
            }
        }

        boolean isFrozen() {
            if (freezeTime == 0) {
                return false;
            }
            if (System.currentTimeMillis() < freezeTime) {
                return true;
            }
            freezeTime = 0;
            attempts = 0;
            return false;
        }
    }

    public enum ServerLoad {
        NORMAL, HIGH
    }
}