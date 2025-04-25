//package com.example.pdfconverter.service;
//
//import io.github.bucket4j.*;
//import io.github.bucket4j.distributed.proxy.ProxyManager;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.util.function.Supplier;
//
//@Service
//public class RedisRateLimiter {
//
//    private final ProxyManager<String> proxyManager;
//
//    @Autowired
//    public RedisRateLimiter(ProxyManager<String> proxyManager) {
//        this.proxyManager = proxyManager;
//    }
//
//    public Bucket resolveBucket(String key, int capacity, Duration duration) {
//        Supplier<BucketConfiguration> configSupplier = getConfigSupplierForUser(capacity, duration);
//        return proxyManager.builder().build(key, configSupplier);
//    }
//
//    private Supplier<BucketConfiguration> getConfigSupplierForUser(int capacity, Duration duration) {
//        Refill refill = Refill.intervally(capacity, duration);
//        Bandwidth limit = Bandwidth.classic(capacity, refill);
//        return () -> BucketConfiguration.builder()
//                .addLimit(limit)
//                .build();
//    }
//}