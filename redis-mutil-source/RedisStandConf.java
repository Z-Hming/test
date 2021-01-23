package com.archly.data.source.endpoint.config;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
public class RedisStandConf {
    private String host;
    private String password;
    private int port;
    private int database;
}
