package com.jwt.service;

import com.jwt.entity.Board;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaStatus;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaLifecycleService {
    private final MediaFileRepository mediaFileRepository;
    private final BoardRepository boardRepository;
    private final ObjectStorageService objectStorageService;
    private final MediaReferenceParser mediaReferenceParser;

    @Transactional
    public void reconcile(Board board, String markdown) {
        Set<Long> referenced = mediaReferenceParser.referencedMediaIds(markdown);
        for (MediaFile media : mediaFileRepository.findAllByBoard(board)) {
            if (referenced.contains(media.getMediaFileId())) {
                media.setStatus(MediaStatus.ACTIVE);
                media.setOrphanedAt(null);
            } else if (media.effectiveStatus() == MediaStatus.ACTIVE) {
                markOrphan(media, LocalDateTime.now());
            }
        }
    }

    @Transactional
    public void markOrphan(Long mediaId) {
        MediaFile media = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("미디어를 찾을 수 없습니다."));
        markOrphan(media, LocalDateTime.now());
    }

    @Transactional
    public void detachForBoardDeletion(Board board) {
        LocalDateTime now = LocalDateTime.now();
        for (MediaFile media : mediaFileRepository.findAllByBoard(board)) {
            markOrphan(media, now);
            media.setBoard(null);
        }
    }

    @Transactional
    public CleanupResult cleanup(LocalDateTime cutoff) {
        int deletedMedia = 0;
        int deletedDrafts = 0;

        markAbandonedUploads(cutoff);
        for (MediaFile media : mediaFileRepository.findAllByStatusAndOrphanedAtBefore(MediaStatus.ORPHAN, cutoff)) {
            if (deleteObject(media)) {
                mediaFileRepository.delete(media);
                deletedMedia++;
            }
        }

        for (Board draft : boardRepository.findAllByDraftTrueAndUpdatedAtBefore(cutoff)) {
            List<MediaFile> mediaFiles = mediaFileRepository.findAllByBoard(draft);
            boolean allDeleted = true;
            for (MediaFile media : mediaFiles) {
                if (deleteObject(media)) {
                    mediaFileRepository.delete(media);
                    deletedMedia++;
                } else {
                    markOrphan(media, media.getOrphanedAt() == null ? draft.getUpdatedAt() : media.getOrphanedAt());
                    allDeleted = false;
                }
            }
            if (allDeleted) {
                boardRepository.delete(draft);
                deletedDrafts++;
            }
        }
        return new CleanupResult(deletedMedia, deletedDrafts);
    }

    private void markAbandonedUploads(LocalDateTime cutoff) {
        for (MediaFile media : mediaFileRepository.findAllByStatusAndCreatedAtBefore(MediaStatus.ACTIVE, cutoff)) {
            Board board = media.getBoard();
            if (board == null) {
                continue;
            }
            String markdown = board.getContentMarkdown() != null ? board.getContentMarkdown() : board.getContent();
            if (!mediaReferenceParser.referencedMediaIds(markdown).contains(media.getMediaFileId())) {
                markOrphan(media, media.getCreatedAt());
            }
        }
    }

    private boolean deleteObject(MediaFile media) {
        try {
            objectStorageService.delete(media.getMediaType(), media.getObjectKey());
            return true;
        } catch (RuntimeException e) {
            log.warn("Object Storage cleanup failed. mediaId={}, objectKey={}", media.getMediaFileId(), media.getObjectKey());
            return false;
        }
    }

    private void markOrphan(MediaFile media, LocalDateTime orphanedAt) {
        media.setStatus(MediaStatus.ORPHAN);
        media.setOrphanedAt(orphanedAt == null ? LocalDateTime.now() : orphanedAt);
    }

    public record CleanupResult(int deletedMedia, int deletedDrafts) {
    }
}
