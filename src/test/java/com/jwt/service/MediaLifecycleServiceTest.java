package com.jwt.service;

import com.jwt.entity.Board;
import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaStatus;
import com.jwt.entity.MediaType;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.MediaFileRepository;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaLifecycleServiceTest {
    @Autowired MediaLifecycleService lifecycleService;
    @Autowired BoardRepository boardRepository;
    @Autowired MediaFileRepository mediaFileRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean ObjectStorageService objectStorageService;

    @Test
    void reconcileMarksRemovedMediaOrphanAndRestoresReferencedMedia() {
        User admin = userRepository.saveAndFlush(admin("lifecycle-reconcile@example.com"));
        Board board = boardRepository.saveAndFlush(board(admin, false));
        MediaFile kept = mediaFileRepository.saveAndFlush(media(board, admin, MediaStatus.ORPHAN));
        MediaFile removed = mediaFileRepository.saveAndFlush(media(board, admin, MediaStatus.ACTIVE));

        lifecycleService.reconcile(board, "![image](/media/" + kept.getMediaFileId() + ")");

        assertThat(kept.effectiveStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(kept.getOrphanedAt()).isNull();
        assertThat(removed.effectiveStatus()).isEqualTo(MediaStatus.ORPHAN);
        assertThat(removed.getOrphanedAt()).isNotNull();
    }

    @Test
    void oldOrphanIsDeletedOnlyAfterObjectStorageDeleteSucceeds() {
        User admin = userRepository.saveAndFlush(admin("lifecycle-delete@example.com"));
        Board board = boardRepository.saveAndFlush(board(admin, false));
        MediaFile orphan = mediaFileRepository.saveAndFlush(media(board, admin, MediaStatus.ORPHAN));
        orphan.setOrphanedAt(LocalDateTime.now().minusHours(25));
        mediaFileRepository.flush();

        lifecycleService.cleanup(LocalDateTime.now().minusHours(24));
        mediaFileRepository.flush();

        assertThat(mediaFileRepository.findById(orphan.getMediaFileId())).isEmpty();
        verify(objectStorageService).delete(orphan.getMediaType(), orphan.getObjectKey());
    }

    @Test
    void objectStorageDeleteFailureKeepsDatabaseRowForRetry() {
        User admin = userRepository.saveAndFlush(admin("lifecycle-retry@example.com"));
        Board board = boardRepository.saveAndFlush(board(admin, false));
        MediaFile orphan = mediaFileRepository.saveAndFlush(media(board, admin, MediaStatus.ORPHAN));
        orphan.setOrphanedAt(LocalDateTime.now().minusHours(25));
        mediaFileRepository.flush();
        doThrow(new IllegalStateException("unavailable"))
                .when(objectStorageService).delete(orphan.getMediaType(), orphan.getObjectKey());

        lifecycleService.cleanup(LocalDateTime.now().minusHours(24));

        assertThat(mediaFileRepository.findById(orphan.getMediaFileId())).isPresent();
    }

    @Test
    void oldDraftAndItsMediaAreCleanedButLegacyUnlinkedRowIsPreserved() {
        User admin = userRepository.saveAndFlush(admin("lifecycle-draft@example.com"));
        Board draft = boardRepository.saveAndFlush(board(admin, true));
        MediaFile draftMedia = mediaFileRepository.saveAndFlush(media(draft, admin, MediaStatus.ACTIVE));
        MediaFile legacy = media(null, admin, null);
        legacy.setCreatedAt(LocalDateTime.now().minusDays(10));
        legacy = mediaFileRepository.saveAndFlush(legacy);

        lifecycleService.cleanup(LocalDateTime.now().plusMinutes(1));
        mediaFileRepository.flush();

        assertThat(boardRepository.findById(draft.getBoardId())).isEmpty();
        assertThat(mediaFileRepository.findById(draftMedia.getMediaFileId())).isEmpty();
        assertThat(mediaFileRepository.findById(legacy.getMediaFileId())).isPresent();
    }

    private User admin(String email) {
        return User.builder()
                .email(email).name("Admin").password("{noop}password")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
    }

    private Board board(User user, boolean draft) {
        Board board = new Board();
        board.setTitle(draft ? "" : "Post");
        board.setContent(draft ? "" : "Body");
        board.setContentMarkdown(draft ? "" : "Body");
        board.setPublished(!draft);
        board.setDraft(draft);
        board.setUser(user);
        return board;
    }

    private MediaFile media(Board board, User uploader, MediaStatus status) {
        MediaFile media = new MediaFile();
        media.setOriginalFilename("image.png");
        media.setObjectKey("images/" + java.util.UUID.randomUUID());
        media.setUrl("");
        media.setMimeType("image/png");
        media.setSize(12L);
        media.setMediaType(MediaType.IMAGE);
        media.setUploader(uploader);
        media.setBoard(board);
        media.setStatus(status);
        if (status == MediaStatus.ORPHAN) {
            media.setOrphanedAt(LocalDateTime.now().minusHours(1));
        }
        return media;
    }
}
