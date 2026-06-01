package com.jwt.dto;

import com.jwt.entity.MediaFile;
import lombok.Getter;

public class MediaDto {

    @Getter
    public static class Response {
        private final Long mediaFileId;
        private final String originalFilename;
        private final String url;
        private final String mimeType;
        private final Long size;
        private final String mediaType;

        public Response(MediaFile mediaFile) {
            this.mediaFileId = mediaFile.getMediaFileId();
            this.originalFilename = mediaFile.getOriginalFilename();
            this.url = mediaFile.getUrl();
            this.mimeType = mediaFile.getMimeType();
            this.size = mediaFile.getSize();
            this.mediaType = mediaFile.getMediaType().name();
        }
    }
}
