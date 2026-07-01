package com.jwt.repository;

import com.jwt.entity.MediaFile;
import com.jwt.entity.MediaStatus;
import com.jwt.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {
    @EntityGraph(attributePaths = {"board", "board.category"})
    Optional<MediaFile> findWithBoardByMediaFileId(Long mediaFileId);

    List<MediaFile> findAllByBoard(Board board);

    List<MediaFile> findAllByStatusAndOrphanedAtBefore(MediaStatus status, LocalDateTime cutoff);

    List<MediaFile> findAllByStatusAndCreatedAtBefore(MediaStatus status, LocalDateTime cutoff);
}
