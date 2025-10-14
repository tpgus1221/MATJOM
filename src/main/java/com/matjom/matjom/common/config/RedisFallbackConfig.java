package com.matjom.matjom.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory noOpRedisConnectionFactory() {
        return new NoOpRedisConnectionFactory();
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate fallbackStringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    private static final class NoOpRedisConnectionFactory implements RedisConnectionFactory {

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisConnection getConnection() {
            throw new UnsupportedOperationException("No Redis connection available in this profile.");
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            throw new UnsupportedOperationException("No Redis cluster connection available in this profile.");
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            throw new UnsupportedOperationException("No Redis sentinel connection available in this profile.");
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }
}
