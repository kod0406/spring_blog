package com.jwt.service;

import com.jwt.entity.MediaType;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorageService {
    StoredObject upload(MediaType mediaType,
                        String objectKey,
                        String contentType,
                        long contentLength,
                        InputStream inputStream);

    StoredContent get(MediaType mediaType, String objectKey);

    StoredContent getRange(MediaType mediaType, String objectKey, ObjectRange range);

    void delete(MediaType mediaType, String objectKey);

    record StoredObject(String objectKey, String eTag) {
    }

    record ObjectRange(Long startByte, Long endByte) {
        public ObjectRange {
            if (startByte == null && endByte == null) {
                throw new IllegalArgumentException("Range 시작 또는 끝 값이 필요합니다.");
            }
            if (startByte != null && startByte < 0 || endByte != null && endByte < 0) {
                throw new IllegalArgumentException("Range 값은 음수일 수 없습니다.");
            }
            if (startByte != null && endByte != null && startByte > endByte) {
                throw new IllegalArgumentException("Range 시작 값이 끝 값보다 클 수 없습니다.");
            }
        }
    }

    record StoredContent(InputStream inputStream,
                         long contentLength,
                         String contentType,
                         String eTag,
                         String contentRange) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
