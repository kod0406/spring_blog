package com.jwt.service;

import com.jwt.config.OciObjectStorageProperties;
import com.jwt.entity.MediaType;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OciObjectStorageServiceTest {
    @Mock
    OciObjectStorageClientFactory clientFactory;

    @Mock
    ObjectStorage imageStorage;

    @Mock
    ObjectStorage videoStorage;

    OciObjectStorageService storageService;

    @BeforeEach
    void setUp() {
        OciObjectStorageProperties properties = new OciObjectStorageProperties();
        properties.setNamespace("test-namespace");
        properties.setImageNamespace("image-namespace");
        properties.setVideoNamespace("video-namespace");
        properties.setImageBucket("private-images");
        properties.setVideoBucket("private-videos");
        when(clientFactory.getClient(any(MediaType.class))).thenAnswer(invocation ->
                invocation.getArgument(0) == MediaType.IMAGE ? imageStorage : videoStorage);
        storageService = new OciObjectStorageService(clientFactory, properties);
    }

    @Test
    void uploadSelectsImageAndVideoBucketsAndKeepsInputStreams() {
        when(imageStorage.putObject(any())).thenReturn(PutObjectResponse.builder().eTag("image-etag").build());
        when(videoStorage.putObject(any())).thenReturn(PutObjectResponse.builder().eTag("video-etag").build());

        storageService.upload(MediaType.IMAGE, "images/a", "image/png", 3,
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        storageService.upload(MediaType.VIDEO, "videos/b", "video/mp4", 4,
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));

        ArgumentCaptor<PutObjectRequest> imageCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<PutObjectRequest> videoCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(imageStorage).putObject(imageCaptor.capture());
        verify(videoStorage).putObject(videoCaptor.capture());
        assertThat(imageCaptor.getValue().getNamespaceName()).isEqualTo("image-namespace");
        assertThat(imageCaptor.getValue().getBucketName()).isEqualTo("private-images");
        assertThat(imageCaptor.getValue().getContentLength()).isEqualTo(3L);
        assertThat(imageCaptor.getValue().getPutObjectBody()).isNotNull();
        assertThat(videoCaptor.getValue().getNamespaceName()).isEqualTo("video-namespace");
        assertThat(videoCaptor.getValue().getBucketName()).isEqualTo("private-videos");
        assertThat(videoCaptor.getValue().getContentLength()).isEqualTo(4L);
        assertThat(videoCaptor.getValue().getPutObjectBody()).isNotNull();
    }

    @Test
    void rangeDownloadAndDeleteUseExpectedBucketAndHeaders() throws Exception {
        when(videoStorage.getObject(any())).thenReturn(GetObjectResponse.builder()
                .inputStream(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)))
                .contentLength(4L)
                .contentType("video/mp4")
                .eTag("etag")
                .headers(Map.of("content-range", List.of("bytes 0-3/10")))
                .build());

        try (ObjectStorageService.StoredContent content = storageService.getRange(
                MediaType.VIDEO, "videos/b", new ObjectStorageService.ObjectRange(0L, 3L))) {
            assertThat(content.inputStream().readAllBytes()).isEqualTo("data".getBytes(StandardCharsets.UTF_8));
            assertThat(content.contentRange()).isEqualTo("bytes 0-3/10");
        }
        storageService.delete(MediaType.VIDEO, "videos/b");

        ArgumentCaptor<GetObjectRequest> getCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(videoStorage).getObject(getCaptor.capture());
        assertThat(getCaptor.getValue().getNamespaceName()).isEqualTo("video-namespace");
        assertThat(getCaptor.getValue().getBucketName()).isEqualTo("private-videos");
        assertThat(getCaptor.getValue().getRange().getStartByte()).isEqualTo(0L);
        assertThat(getCaptor.getValue().getRange().getEndByte()).isEqualTo(3L);

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(videoStorage).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().getNamespaceName()).isEqualTo("video-namespace");
        assertThat(deleteCaptor.getValue().getBucketName()).isEqualTo("private-videos");
    }
}
