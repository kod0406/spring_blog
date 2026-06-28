package com.jwt.service;

import com.jwt.config.OciObjectStorageProperties;
import com.jwt.entity.MediaType;
import com.oracle.bmc.model.Range;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OciObjectStorageService implements ObjectStorageService {
    private final OciObjectStorageClientFactory clientFactory;
    private final OciObjectStorageProperties properties;

    @Override
    public StoredObject upload(MediaType mediaType,
                               String objectKey,
                               String contentType,
                               long contentLength,
                               InputStream inputStream) {
        try {
            PutObjectResponse response = client().putObject(PutObjectRequest.builder()
                    .namespaceName(namespace())
                    .bucketName(bucket(mediaType))
                    .objectName(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .putObjectBody(inputStream)
                    .build());
            return new StoredObject(objectKey, response.getETag());
        } catch (BmcException e) {
            throw storageException("OCI 객체 업로드에 실패했습니다.", e);
        }
    }

    @Override
    public StoredContent get(MediaType mediaType, String objectKey) {
        return get(mediaType, objectKey, null);
    }

    @Override
    public StoredContent getRange(MediaType mediaType, String objectKey, ObjectRange range) {
        if (range == null) {
            throw new IllegalArgumentException("Range 값이 필요합니다.");
        }
        return get(mediaType, objectKey, range);
    }

    @Override
    public void delete(MediaType mediaType, String objectKey) {
        try {
            client().deleteObject(DeleteObjectRequest.builder()
                    .namespaceName(namespace())
                    .bucketName(bucket(mediaType))
                    .objectName(objectKey)
                    .build());
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                return;
            }
            throw storageException("OCI 객체 삭제에 실패했습니다.", e);
        }
    }

    private StoredContent get(MediaType mediaType, String objectKey, ObjectRange range) {
        try {
            GetObjectRequest.Builder builder = GetObjectRequest.builder()
                    .namespaceName(namespace())
                    .bucketName(bucket(mediaType))
                    .objectName(objectKey);
            if (range != null) {
                builder.range(new Range(range.startByte(), range.endByte()));
            }
            GetObjectResponse response = client().getObject(builder.build());
            return new StoredContent(
                    response.getInputStream(),
                    response.getContentLength(),
                    response.getContentType(),
                    response.getETag(),
                    firstHeader(response.getHeaders(), "content-range")
            );
        } catch (BmcException e) {
            throw storageException("OCI 객체 조회에 실패했습니다.", e);
        }
    }

    private ObjectStorage client() {
        return clientFactory.getClient();
    }

    private String namespace() {
        return require(properties.getNamespace(), "OCI namespace 설정이 없습니다.");
    }

    private String bucket(MediaType mediaType) {
        return require(mediaType == MediaType.IMAGE ? properties.getImageBucket() : properties.getVideoBucket(),
                mediaType == MediaType.IMAGE ? "OCI 이미지 버킷 설정이 없습니다." : "OCI 동영상 버킷 설정이 없습니다.");
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst()
                .orElse(null);
    }

    private ObjectStorageException storageException(String message, BmcException cause) {
        return new ObjectStorageException(message, cause, cause.getStatusCode() == 404);
    }
}
