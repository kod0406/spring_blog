package com.jwt.service;

import com.jwt.dto.MediaDto;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaType;
import com.jwt.entity.User;
import com.jwt.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {
    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long VIDEO_MAX_BYTES = 60L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");

    private final AuthorizationService authorizationService;
    private final ObjectStorageService objectStorageService;
    private final MediaFileRepository mediaFileRepository;

    @Transactional
    public MediaDto.Response uploadImage(MultipartFile file, User user) {
        return upload(file, user, MediaType.IMAGE);
    }

    @Transactional
    public MediaDto.Response uploadVideo(MultipartFile file, User user) {
        return upload(file, user, MediaType.VIDEO);
    }

    private MediaDto.Response upload(MultipartFile file, User user, MediaType mediaType) {
        authorizationService.requireAdmin(user);
        validate(file, mediaType);

        try {
            String objectKey = mediaType.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + "-" + cleanFilename(file.getOriginalFilename());
            ObjectStorageService.StoredObject storedObject = objectStorageService.upload(
                    mediaType,
                    objectKey,
                    file.getContentType(),
                    file.getBytes()
            );

            MediaFile mediaFile = new MediaFile();
            mediaFile.setOriginalFilename(file.getOriginalFilename());
            mediaFile.setObjectKey(storedObject.objectKey());
            mediaFile.setUrl(storedObject.url());
            mediaFile.setMimeType(file.getContentType());
            mediaFile.setSize(file.getSize());
            mediaFile.setMediaType(mediaType);
            mediaFile.setUploader(user);
            return new MediaDto.Response(mediaFileRepository.save(mediaFile));
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
        }
    }

    private void validate(MultipartFile file, MediaType mediaType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        String contentType = file.getContentType();
        if (mediaType == MediaType.IMAGE) {
            if (!IMAGE_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("허용되지 않는 이미지 형식입니다.");
            }
            if (file.getSize() > IMAGE_MAX_BYTES) {
                throw new IllegalArgumentException("이미지는 5MB 이하만 업로드할 수 있습니다.");
            }
            return;
        }

        if (!VIDEO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("허용되지 않는 영상 형식입니다.");
        }
        if (file.getSize() > VIDEO_MAX_BYTES) {
            throw new IllegalArgumentException("영상은 60MB 이하만 업로드할 수 있습니다.");
        }
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
