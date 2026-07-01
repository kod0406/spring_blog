package com.jwt.service;

import com.jwt.config.OciObjectStorageProperties;
import com.jwt.entity.MediaType;
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
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OciObjectStorageClientFactory {
    private final OciObjectStorageProperties properties;
    private final Map<String, ObjectStorage> clientsByRegion = new ConcurrentHashMap<>();

    public ObjectStorage getClient(MediaType mediaType) {
        String regionId = regionFor(mediaType);
        return clientsByRegion.computeIfAbsent(regionId, this::createClient);
    }

    private ObjectStorage createClient(String regionId) {
        AbstractAuthenticationDetailsProvider provider = authenticationProvider();
        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(regionId))
                .build(provider);
    }

    String regionFor(MediaType mediaType) {
        if (mediaType == null) {
            throw new IllegalArgumentException("미디어 유형이 필요합니다.");
        }
        String configured = mediaType == MediaType.IMAGE
                ? properties.getImageRegion()
                : properties.getVideoRegion();
        if (configured == null || configured.isBlank()) {
            configured = properties.getRegion();
        }
        return require(configured, mediaType == MediaType.IMAGE
                ? "OCI 이미지 리전 설정이 없습니다."
                : "OCI 동영상 리전 설정이 없습니다.");
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
        Exception failure = null;
        for (ObjectStorage client : clientsByRegion.values()) {
            try {
                client.close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        clientsByRegion.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
