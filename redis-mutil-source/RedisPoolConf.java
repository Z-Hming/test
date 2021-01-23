package com.archly.data.source.endpoint.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Data
@ToString
@ConfigurationProperties(prefix = "spring.redis.lettuce.pool")
@Component
public class RedisPoolConf {
    private int maxActive;
    private int maxIdle;
    private int maxWait;
    private int minIdle;
}
