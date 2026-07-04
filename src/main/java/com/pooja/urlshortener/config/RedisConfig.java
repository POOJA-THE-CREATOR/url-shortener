package com.pooja.urlshortener.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cache-aside setup: reads check Redis first and only fall through to
 * Postgres on a miss. Short TTL keeps stale data bounded without needing
 * explicit invalidation on every write. This is the piece that lets the
 * redirect path stay fast under load — most traffic to a shortener skews
 * heavily toward a small set of hot links (power-law access pattern), so
 * a cache in front of the DB absorbs the majority of read traffic.
 *
 * Disabled under the "test" profile so `mvn test` doesn't need a real
 * Redis instance running — tests fall back to Spring's default in-memory
 * cache manager (see application-test.properties: spring.cache.type=simple).
 */
@Configuration
@EnableCaching
@Profile("!test")
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("urlCache", config)
                .build();
    }
}
