package com.jwt.service;

import com.jwt.dto.MediaDto;
import com.jwt.entity.Board;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaStatus;
import com.jwt.entity.MediaType;
import com.jwt.entity.User;
import com.jwt.exception.NotFoundException;
import com.jwt.exception.RangeNotSatisfiableException;
import com.jwt.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {
    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long VIDEO_MAX_BYTES = 60L * 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 180;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "application/mp4");

    private final AuthorizationService authorizationService;
    private final ObjectStorageService objectStorageService;
    private final MediaFileRepository mediaFileRepository;
    private final MediaDtoMapper mediaDtoMapper;
    private final BoardService boardService;
    private final MediaLifecycleService mediaLifecycleService;
    private final Tika tika = new Tika();

    @Transactional
    public MediaDto.Response uploadImage(Long postId, MultipartFile file, User user) {
        return upload(postId, file, user, MediaType.IMAGE);
    }

    @Transactional
    public MediaDto.Response uploadVideo(Long postId, MultipartFile file, User user) {
        return upload(postId, file, user, MediaType.VIDEO);
    }

    @Transactional(readOnly = true)
    public MediaDownload open(Long mediaId, User viewer, ObjectStorageService.ObjectRange requestedRange) {
        MediaFile media = mediaFileRepository.findWithBoardByMediaFileId(mediaId)
                .orElseThrow(() -> new NotFoundException("미디어를 찾을 수 없습니다."));
        Board board = media.getBoard();
        if (media.effectiveStatus() != MediaStatus.ACTIVE || board == null) {
            throw new NotFoundException("미디어를 찾을 수 없습니다.");
        }

        boolean admin = authorizationService.isAdmin(viewer);
        boolean publiclyReadable = boardService.isPubliclyReadable(board);
        if (!admin && !publiclyReadable) {
            throw new NotFoundException("미디어를 찾을 수 없습니다.");
        }

        ObjectStorageService.ObjectRange normalizedRange = normalizeRange(requestedRange, media.getSize());
        try {
            ObjectStorageService.StoredContent content = normalizedRange == null
                    ? objectStorageService.get(media.getMediaType(), media.getObjectKey())
                    : objectStorageService.getRange(media.getMediaType(), media.getObjectKey(), normalizedRange);
            String contentRange = normalizedRange == null
                    ? null
                    : "bytes " + normalizedRange.startByte() + "-" + normalizedRange.endByte() + "/" + media.getSize();
            return new MediaDownload(
                    media.getOriginalFilename(),
                    media.getMimeType(),
                    media.getSize(),
                    normalizedRange != null,
                    content,
                    content.contentRange() == null ? contentRange : content.contentRange()
            );
        } catch (ObjectStorageException e) {
            if (e.isNotFound()) {
                throw new NotFoundException("미디어를 찾을 수 없습니다.");
            }
            throw new IllegalStateException("미디어 저장소에 연결할 수 없습니다.", e);
        }
    }

    public void markOrphan(Long mediaId, User user) {
        authorizationService.requireAdmin(user);
        mediaLifecycleService.markOrphan(mediaId);
    }

    private MediaDto.Response upload(Long postId, MultipartFile file, User user, MediaType mediaType) {
        authorizationService.requireAdmin(user);
        if (postId == null) {
            throw new IllegalArgumentException("미디어를 연결할 postId가 필요합니다.");
        }
        Board board = boardService.getPostEntity(postId);
        validateBasic(file, mediaType);
        String detectedType = detectType(file, mediaType);
        String filename = safeFilename(file.getOriginalFilename());
        String objectKey = (mediaType == MediaType.IMAGE ? "images/" : "videos/") + UUID.randomUUID();

        boolean uploaded = false;
        try (InputStream inputStream = file.getInputStream()) {
            ObjectStorageService.StoredObject storedObject = objectStorageService.upload(
                    mediaType,
                    objectKey,
                    detectedType,
                    file.getSize(),
                    inputStream
            );
            uploaded = true;

            MediaFile mediaFile = new MediaFile();
            mediaFile.setOriginalFilename(filename);
            mediaFile.setObjectKey(storedObject.objectKey());
            mediaFile.setUrl("");
            mediaFile.setMimeType(normalizeContentType(detectedType));
            mediaFile.setSize(file.getSize());
            mediaFile.setMediaType(mediaType);
            mediaFile.setUploader(user);
            mediaFile.setBoard(board);
            mediaFile.setStatus(MediaStatus.ACTIVE);
            mediaFile.setOrphanedAt(null);
            return mediaDtoMapper.toResponse(mediaFileRepository.saveAndFlush(mediaFile));
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
        } catch (RuntimeException e) {
            if (uploaded) {
                compensateUpload(mediaType, objectKey);
            }
            if (e instanceof ObjectStorageException) {
                throw new IllegalStateException("미디어 저장소에 업로드할 수 없습니다.", e);
            }
            throw e;
        }
    }

    private void validateBasic(MultipartFile file, MediaType mediaType) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        long maxBytes = mediaType == MediaType.IMAGE ? IMAGE_MAX_BYTES : VIDEO_MAX_BYTES;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(mediaType == MediaType.IMAGE
                    ? "이미지는 5MB 이하만 업로드할 수 있습니다."
                    : "영상은 60MB 이하만 업로드할 수 있습니다.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("파일명은 180자 이하만 사용할 수 있습니다.");
        }
    }

    private String detectType(MultipartFile file, MediaType mediaType) {
        try (InputStream inputStream = file.getInputStream()) {
            String detectedType = tika.detect(inputStream, file.getOriginalFilename());
            Set<String> allowed = mediaType == MediaType.IMAGE ? IMAGE_TYPES : VIDEO_TYPES;
            if (!allowed.contains(detectedType)) {
                throw new IllegalArgumentException(mediaType == MediaType.IMAGE
                        ? "실제 이미지 형식을 확인할 수 없거나 허용되지 않는 형식입니다."
                        : "실제 영상 형식을 확인할 수 없거나 허용되지 않는 형식입니다.");
            }
            return detectedType;
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 형식을 확인할 수 없습니다.");
        }
    }

    private String normalizeContentType(String detectedType) {
        return "application/mp4".equals(detectedType) ? "video/mp4" : detectedType;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String leaf = filename.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        String sanitized = leaf.replaceAll("[\\r\\n\\t\\u0000-\\u001f\\u007f]", "_").trim();
        return sanitized.isEmpty() ? "upload" : sanitized;
    }

    private ObjectStorageService.ObjectRange normalizeRange(ObjectStorageService.ObjectRange requested, long totalSize) {
        if (requested == null) {
            return null;
        }
        if (totalSize <= 0) {
            throw new RangeNotSatisfiableException("비어 있는 미디어에는 Range를 적용할 수 없습니다.");
        }

        long start;
        long end;
        if (requested.startByte() == null) {
            long suffixLength = requested.endByte();
            if (suffixLength <= 0) {
                throw new RangeNotSatisfiableException("유효하지 않은 Range입니다.");
            }
            start = Math.max(0, totalSize - suffixLength);
            end = totalSize - 1;
        } else {
            start = requested.startByte();
            end = requested.endByte() == null ? totalSize - 1 : Math.min(requested.endByte(), totalSize - 1);
        }
        if (start >= totalSize || start > end) {
            throw new RangeNotSatisfiableException("요청한 Range가 파일 크기를 벗어났습니다.");
        }
        return new ObjectStorageService.ObjectRange(start, end);
    }

    private void compensateUpload(MediaType mediaType, String objectKey) {
        try {
            objectStorageService.delete(mediaType, objectKey);
        } catch (RuntimeException cleanupError) {
            log.error("Failed to compensate Object Storage upload. objectKey={}", objectKey);
        }
    }

    public record MediaDownload(String originalFilename,
                                String mimeType,
                                long totalSize,
                                boolean partial,
                                ObjectStorageService.StoredContent content,
                                String contentRange) {
    }
}
