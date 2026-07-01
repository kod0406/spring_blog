package com.jwt.service;

import com.jwt.config.OciObjectStorageProperties;
import com.jwt.entity.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("oci-integration")
@SpringBootTest
@ActiveProfiles("oci-integration")
class OciObjectStorageManualIntegrationTest {
    @Autowired OciObjectStorageService storageService;
    @Autowired OciObjectStorageProperties properties;

    @Test
    void uploadReadRangeAndDeleteAgainstConfiguredPrivateImageBucket() throws Exception {
        assumeTrue(hasText(properties.getRegion()));
        assumeTrue(hasText(properties.getNamespace()));
        assumeTrue(hasText(properties.getImageBucket()));

        byte[] payload = "oci-manual-test".getBytes(StandardCharsets.UTF_8);
        String objectKey = "manual-tests/" + UUID.randomUUID();
        storageService.upload(MediaType.IMAGE, objectKey, "text/plain", payload.length,
                new ByteArrayInputStream(payload));
        try {
            try (ObjectStorageService.StoredContent full = storageService.get(MediaType.IMAGE, objectKey)) {
                assertThat(full.inputStream().readAllBytes()).isEqualTo(payload);
            }
            try (ObjectStorageService.StoredContent range = storageService.getRange(
                    MediaType.IMAGE, objectKey, new ObjectStorageService.ObjectRange(0L, 2L))) {
                assertThat(range.inputStream().readAllBytes()).isEqualTo("oci".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            storageService.delete(MediaType.IMAGE, objectKey);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
