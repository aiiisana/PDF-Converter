package com.example.pdfconverter.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    public static final int FREE_LIMIT = 1;
    public static final int PRO_LIMIT = 15;
    public static final int VIP_LIMIT = 50;
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int FILE_GENERATION_LIMIT = 5;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration FREEZE_DURATION = Duration.ofMinutes(30);

    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final RedisRateLimitRepository redisRepo;

    private ServerLoad serverLoad = ServerLoad.NORMAL;

    @Autowired
    public RateLimitService(RedisRateLimitRepository redisRepo) {
        this.redisRepo = redisRepo;
    }

    public boolean isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        if (fileSize > MAX_FILE_SIZE) return false;

        int generations = redisRepo.getFileGenerations(fileHash);
        if (generations >= FILE_GENERATION_LIMIT) return false;

        AttemptInfo attemptInfo = redisRepo.getAttempts(userId);
        if (attemptInfo != null && attemptInfo.isFrozen()) return false;

        Bucket bucket = userBuckets.computeIfAbsent(userId, k -> createBucketForSubscription(subscriptionType));
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            incrementAttempts(userId);
        } else {
            resetAttempts(userId);
            redisRepo.incrementFileGenerations(fileHash);
        }

        return allowed && !isServerOverloaded();
    }

    private Bucket createBucketForSubscription(String subscriptionType) {
        int limit = switch (subscriptionType.toLowerCase()) {
            case "pro" -> PRO_LIMIT;
            case "vip" -> VIP_LIMIT;
            default -> FREE_LIMIT;
        };

        LocalBucketBuilder builder = Bucket4j.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, Duration.ofDays(1))));

        if (serverLoad == ServerLoad.HIGH) {
            builder.addLimit(Bandwidth.simple(limit / 2, Duration.ofMinutes(1)));
        }

        return builder.build();
    }

    private void incrementAttempts(String userId) {
        AttemptInfo info = redisRepo.getAttempts(userId);
        if (info == null) info = new AttemptInfo(1);
        else info.incrementAttempts();
        redisRepo.saveAttempts(userId, info, FREEZE_DURATION);
    }

    private void resetAttempts(String userId) {
        redisRepo.deleteAttempts(userId);
    }

    private boolean isServerOverloaded() {
        return serverLoad == ServerLoad.HIGH;
    }

    public void updateServerLoad(ServerLoad load) {
        this.serverLoad = load;
    }

    public enum ServerLoad {
        NORMAL, HIGH
    }
}