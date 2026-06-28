package com.jwt.service;

import com.jwt.config.OciObjectStorageProperties;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OciObjectStorageClientFactory {
    private final OciObjectStorageProperties properties;
    private volatile ObjectStorage client;

    public ObjectStorage getClient() {
        ObjectStorage current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = createClient();
            }
            return client;
        }
    }

    private ObjectStorage createClient() {
        String regionId = require(properties.getRegion(), "OCI region 설정이 없습니다.");
        AbstractAuthenticationDetailsProvider provider = authenticationProvider();
        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(regionId))
                .build(provider);
    }

    private AbstractAuthenticationDetailsProvider authenticationProvider() {
        String mode = require(properties.getAuthMode(), "OCI 인증 모드 설정이 없습니다.")
                .toLowerCase(Locale.ROOT);
        try {
            if ("instance-principal".equals(mode)) {
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            }
            if ("config-file".equals(mode)) {
                String configFile = require(properties.getConfigFile(), "OCI config file 경로가 없습니다.");
                String profile = properties.getProfile() == null || properties.getProfile().isBlank()
                        ? "DEFAULT"
                        : properties.getProfile().trim();
                return new ConfigFileAuthenticationDetailsProvider(configFile, profile);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("OCI 인증 설정을 초기화할 수 없습니다.", e);
        }
        throw new IllegalStateException("지원하지 않는 OCI 인증 모드입니다: " + mode);
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    @PreDestroy
    void close() throws Exception {
        ObjectStorage current = client;
        if (current != null) {
            current.close();
        }
    }
}
