package com.jwt.service;

import com.jwt.dto.MediaDto;
import com.jwt.entity.MediaFile;
import org.springframework.stereotype.Component;

@Component
public class MediaDtoMapper {
    public MediaDto.Response toResponse(MediaFile mediaFile) {
        return new MediaDto.Response(mediaFile);
    }
}
