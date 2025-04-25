//package com.example.pdfconverter.config;
//
//import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
//import io.lettuce.core.RedisClient;
//import io.lettuce.core.api.StatefulRedisConnection;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class Bucket4jConfig {
//
//    @Bean
//    public RedisClient redisClient() {
//        return RedisClient.create("redis://localhost:6379");
//    }
//
//    @Bean
//    public StatefulRedisConnection<byte[], byte[]> statefulRedisConnection(RedisClient redisClient) {
//        return redisClient.connect(new io.lettuce.core.codec.ByteArrayCodec());
//    }
//
//    @Bean
//    public LettuceBasedProxyManager proxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
//        return LettuceBasedProxyManager
//                .builderFor(connection.async())
//                .build();
//    }
//}