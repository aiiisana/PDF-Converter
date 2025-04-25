package com.example.pdfconverter.service;

import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimitService {

    public static final int FREE_LIMIT = 10;
    public static final int PRO_LIMIT = 50;
    public static final int VIP_LIMIT = 200;
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int FILE_GENERATION_LIMIT = 5;
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration FREEZE_DURATION = Duration.ofMinutes(30);
    public static final Duration RESET_PERIOD = Duration.ofHours(1);

    private final ProxyManager<String> proxyManager;
    private final RedisRateLimitRepository redisRepo;

    @Autowired
    public RateLimitService(ProxyManager<String> proxyManager, RedisRateLimitRepository redisRepo) {
        this.proxyManager = proxyManager;
        this.redisRepo = redisRepo;
    }

    public boolean isAllowed(String userId, String subscriptionType, long fileSize, String fileHash) {
        if (fileSize > MAX_FILE_SIZE) return false;

        int generations = redisRepo.getFileGenerations(fileHash);
        if (generations >= FILE_GENERATION_LIMIT) return false;

        AttemptInfo attemptInfo = redisRepo.getAttempts(userId);
        if (attemptInfo != null && attemptInfo.isFrozen()) return false;

        Bucket bucket = resolveBucket(userId, subscriptionType);
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            incrementAttempts(userId);
        } else {
            resetAttempts(userId);
            redisRepo.incrementFileGenerations(fileHash);
        }

        return allowed;
    }

    private Bucket resolveBucket(String userId, String subscriptionType) {
        int limit = switch (subscriptionType.toLowerCase()) {
            case "pro" -> PRO_LIMIT;
            case "vip" -> VIP_LIMIT;
            default -> FREE_LIMIT;
        };

        Supplier<BucketConfiguration> configSupplier = () ->
                BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, RESET_PERIOD)))
                        .build();

        return proxyManager.builder().build(userId, configSupplier);
    }

    private void incrementAttempts(String userId) {
        AttemptInfo info = redisRepo.getAttempts(userId);
        if (info == null) {
            info = new AttemptInfo(1);
        } else {
            info.incrementAttempts();
        }
        redisRepo.saveAttempts(userId, info, FREEZE_DURATION);
    }

    private void resetAttempts(String userId) {
        redisRepo.deleteAttempts(userId);
    }
}