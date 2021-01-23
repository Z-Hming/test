package com.archly.data.source.endpoint.config;

import com.archly.data.source.endpoint.web.RedisTaskExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;


@Configuration
@Slf4j
@EnableCaching
public class RedisConf extends CachingConfigurerSupport {


    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private Jackson2JsonRedisSerializer<Object> jacksonSerializer;

    /**
     * 配置lettuce连接池
     */
    @Bean
    public GenericObjectPoolConfig redisPool(RedisPoolConf conf) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(conf.getMaxIdle());
        poolConfig.setMaxWaitMillis(conf.getMaxWait());
        poolConfig.setMinIdle(conf.getMinIdle());
        poolConfig.setMaxTotal(conf.getMaxActive());
        return poolConfig;
    }


    @Bean("data-conf")
    @ConfigurationProperties(prefix = "spring.data-redis")
    @Primary
    public RedisStandConf dataRedisCon() {
        return new RedisStandConf();
    }

    @Bean("session-conf")
    @ConfigurationProperties(prefix = "spring.session-redis")
    public RedisStandConf sessionRedisCon() {
        return new RedisStandConf();
    }

    /**
     * 原来的数据源
     */
    @Bean("data-config")
    @Primary
    public RedisStandaloneConfiguration dataRedisConfig(@Qualifier("data-conf") RedisStandConf conf) {
        return buildConfiguration(conf);
    }

    /**
     * 用于存储的的数据源
     */
    @Bean("session-config")
    public RedisStandaloneConfiguration sessionRedisConfig(@Qualifier("session-conf") RedisStandConf conf) {
        return buildConfiguration(conf);
    }


    private RedisStandaloneConfiguration buildConfiguration(RedisStandConf conf) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setDatabase(conf.getDatabase());
        configuration.setPassword(RedisPassword.of(conf.getPassword()));
        configuration.setHostName(conf.getHost());
        configuration.setPort(conf.getPort());
        return configuration;
    }


    /**
     * 配置第一个数据源的连接工厂
     * 这里注意：需要添加@Primary 指定bean的名称，目的是为了创建两个不同名称的LettuceConnectionFactory
     */
    @Bean("dataFactory")
    @Primary
    public LettuceConnectionFactory dataFactory(GenericObjectPoolConfig config,
                                                @Qualifier("data-config") RedisStandaloneConfiguration redisConfig) {
        LettuceClientConfiguration clientConfiguration = LettucePoolingClientConfiguration.builder().poolConfig(config).build();
        return new LettuceConnectionFactory(redisConfig, clientConfiguration);
    }

    @Bean("sessionFactory")
    public LettuceConnectionFactory sessionFactory(GenericObjectPoolConfig config,
                                                   @Qualifier("session-config") RedisStandaloneConfiguration redisSessionConfig) {
        LettuceClientConfiguration clientConfiguration = LettucePoolingClientConfiguration.builder().poolConfig(config).build();
        return new LettuceConnectionFactory(redisSessionConfig, clientConfiguration);
    }

    /**
     * 配置第一个数据源的RedisTemplate
     * 注意：这里指定使用名称=factory 的 RedisConnectionFactory
     * 并且标识第一个数据源是默认数据源 @Primary
     */
    @Bean("redis")
    @Primary
    public RedisTemplate<String, Object> dataRedisTemplate(@Qualifier("dataFactory") RedisConnectionFactory factory) {
        return buildRedisTemplate(factory);
    }

    /**
     * 配置第一个数据源的RedisTemplate
     * 注意：这里指定使用名称=factory2 的 RedisConnectionFactory
     */
    @Bean("sessionRedis")
    public RedisTemplate<String, Object> sessionRedisTemplate(@Qualifier("sessionFactory") RedisConnectionFactory sessionFactory) {
        return buildRedisTemplate(sessionFactory);
    }

    public RedisTemplate<String, Object> buildRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jacksonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jacksonSerializer);
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        return template;
    }


    @Bean(destroyMethod = "stop")
    RedisMessageListenerContainer container(@Qualifier("sessionFactory") RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(RedisTaskExecutors.getInstance().getDelegate());
        return container;
    }


    @Override
    @Bean
    public CacheManager cacheManager() {

        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jacksonSerializer));
        return new RedisCacheManager(redisCacheWriter, redisCacheConfiguration);
    }


    @Override
    @Bean
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getName());
            sb.append(":").append(method.getName()).append(":");
            for (Object obj : params) {
                sb.append(obj.toString());
            }
            return sb.toString();
        };
    }


    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.error("handleCacheGetError key = {}, value = {}", key, cache);
                log.error("cache get error", exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.error("handleCachePutError key = {}, value = {}", key, cache);
                log.error("cache put error", exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("handleCacheEvictError key = {}, value = {}", key, cache);
                log.error("cache evict error", exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("handleCacheClearError value = {}", cache);
                log.error("cache clear error", exception);
            }
        };
    }


}

