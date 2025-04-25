package com.example.pdfconverter.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisRateLimitRepository {

    private static final String ATTEMPTS_PREFIX = "attempts:";
    private static final String FILE_GEN_PREFIX = "file_gen:";

    private final ValueOperations<String, Object> valueOps;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimitRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public AttemptInfo getAttempts(String userId) {
        return (AttemptInfo) valueOps.get(ATTEMPTS_PREFIX + userId);
    }

    public void saveAttempts(String userId, AttemptInfo info, Duration ttl) {
        valueOps.set(ATTEMPTS_PREFIX + userId, info, ttl);
    }

    public void deleteAttempts(String userId) {
        redisTemplate.delete(ATTEMPTS_PREFIX + userId);
    }

    public int getFileGenerations(String fileHash) {
        Integer val = (Integer) valueOps.get(FILE_GEN_PREFIX + fileHash);
        return val != null ? val : 0;
    }

    public void incrementFileGenerations(String fileHash) {
        valueOps.increment(FILE_GEN_PREFIX + fileHash);
        redisTemplate.expire(FILE_GEN_PREFIX + fileHash, Duration.ofHours(24));
    }
}