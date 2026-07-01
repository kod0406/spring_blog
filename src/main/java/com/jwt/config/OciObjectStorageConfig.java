package com.jwt.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OciObjectStorageProperties.class)
public class OciObjectStorageConfig {
}
