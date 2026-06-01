package com.jwt.service;

import com.jwt.entity.MediaType;

public interface ObjectStorageService {
    StoredObject upload(MediaType mediaType, String objectKey, String contentType, byte[] bytes);

    record StoredObject(String objectKey, String url) {
    }
}
