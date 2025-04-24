package com.example.pdfconverter.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisRateLimitRepository {

    private final ValueOperations<String, Object> ops;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimitRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.ops = redisTemplate.opsForValue();
    }

    public AttemptInfo getAttempts(String userId) {
        return (AttemptInfo) ops.get("attempts:" + userId);
    }

    public void saveAttempts(String userId, AttemptInfo info, Duration ttl) {
        ops.set("attempts:" + userId, info, ttl);
    }

    public void deleteAttempts(String userId) {
        redisTemplate.delete("attempts:" + userId);
    }

    public int getFileGenerations(String fileHash) {
        Integer val = (Integer) ops.get("file:" + fileHash);
        return val == null ? 0 : val;
    }

    public void incrementFileGenerations(String fileHash) {
        ops.increment("file:" + fileHash);
    }
}