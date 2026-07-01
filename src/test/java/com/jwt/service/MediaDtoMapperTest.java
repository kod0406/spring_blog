package com.jwt.service;

import com.jwt.dto.MediaDto;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDtoMapperTest {
    @Test
    void responseNeverExposesStoredLegacyOrOciUrl() {
        MediaFile media = new MediaFile();
        media.setMediaFileId(77L);
        media.setOriginalFilename("image.png");
        media.setUrl("https://objectstorage.example.invalid/private-object");
        media.setMimeType("image/png");
        media.setSize(10L);
        media.setMediaType(MediaType.IMAGE);

        MediaDto.Response response = new MediaDtoMapper().toResponse(media);

        assertThat(response.getUrl()).isEqualTo("/media/77");
        assertThat(response.getUrl()).doesNotContain("objectstorage");
    }
}
