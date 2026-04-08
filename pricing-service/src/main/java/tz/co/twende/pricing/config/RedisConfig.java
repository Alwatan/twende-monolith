package tz.co.twende.pricing.config;

import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {

    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = JsonMapper.builder().build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashValueSerializer(jsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration cacheConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        jsonSerializer()))
                        .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory).cacheDefaults(cacheConfig).build();
    }
}
