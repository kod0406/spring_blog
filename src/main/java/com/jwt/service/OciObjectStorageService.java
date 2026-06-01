package com.jwt.service;

import com.jwt.entity.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OciObjectStorageService implements ObjectStorageService {
    @Value("${oci.object-storage.enabled:false}")
    private boolean enabled;

    @Value("${oci.object-storage.image-bucket:}")
    private String imageBucket;

    @Value("${oci.object-storage.video-bucket:}")
    private String videoBucket;

    @Value("${oci.object-storage.public-base-url:}")
    private String publicBaseUrl;

    @Override
    public StoredObject upload(MediaType mediaType, String objectKey, String contentType, byte[] bytes) {
        if (!enabled || publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("OCI Object Storage가 아직 설정되지 않았습니다.");
        }

        String bucket = mediaType == MediaType.IMAGE ? imageBucket : videoBucket;
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("OCI 버킷 설정이 없습니다.");
        }

        throw new IllegalStateException("OCI 인증키와 SDK 연결은 이후 단계에서 활성화해야 합니다.");
    }
}
