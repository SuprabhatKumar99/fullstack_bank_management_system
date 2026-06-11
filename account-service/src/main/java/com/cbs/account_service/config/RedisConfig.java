package com.cbs.account_service.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis cache configuration.
 *
 * Cache keys used in AccountService:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ Cache name      │ Key pattern            │ TTL              │
 * ├──────────────────────────────────────────────────────────────┤
 * │ account-balance │ balance::{accountId}   │ 5 minutes        │
 * │ account-detail  │ account::{accountId}   │ 10 minutes       │
 * │ customer-accounts│ cust-accts::{custId}  │ 10 minutes       │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Cache invalidation strategy:
 * - account-balance: evicted on every balance change (Transaction Service
 *   calls evictBalance() after writing ledger entries).
 * - account-detail:  evicted on status change, freeze, close, update.
 * - customer-accounts: evicted when any account for the customer changes.
 *
 * TTL is a safety net — primary invalidation is explicit eviction.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_BALANCE          = "account-balance";
    public static final String CACHE_ACCOUNT_DETAIL   = "account-detail";
    public static final String CACHE_CUSTOMER_ACCOUNTS = "customer-accounts";

    @Value("${spring.cache.redis.time-to-live:300000}")
    private long defaultTtlMillis;

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Store type info so Jackson can deserialize polymorphic objects
        mapper.activateDefaultTyping(
            mapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory factory,
            ObjectMapper redisObjectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory factory,
            ObjectMapper redisObjectMapper) {

        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofMillis(defaultTtlMillis))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(jsonSerializer));

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            CACHE_BALANCE,
                defaultConfig.entryTtl(Duration.ofMinutes(5)),
            CACHE_ACCOUNT_DETAIL,
                defaultConfig.entryTtl(Duration.ofMinutes(10)),
            CACHE_CUSTOMER_ACCOUNTS,
                defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}