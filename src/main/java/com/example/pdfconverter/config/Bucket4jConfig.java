package com.example.pdfconverter.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import redis.clients.jedis.JedisPool;

@Configuration
public class Bucket4jConfig {

    @Bean
    public ProxyManager<String> proxyManager(RedisConnectionFactory connectionFactory) {
        JedisPool jedisPool = new JedisPool(((LettuceConnectionFactory) connectionFactory).getStandaloneConfiguration());
        return JedisBasedProxyManager.builderFor(jedisPool)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(24)))
                .build();
    }
}