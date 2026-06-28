package com.jwt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "media_file", indexes = {
        @Index(name = "idx_media_board", columnList = "board_id"),
        @Index(name = "idx_media_status_orphaned", columnList = "status,orphaned_at")
})
@Data
public class MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mediaFileId;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String objectKey;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id")
    private User uploader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private MediaStatus status = MediaStatus.ACTIVE;

    @Column(name = "orphaned_at")
    private LocalDateTime orphanedAt;

    private LocalDateTime createdAt;

    @PrePersist
    void created() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = MediaStatus.ACTIVE;
        }
    }

    public MediaStatus effectiveStatus() {
        return status == null ? MediaStatus.ACTIVE : status;
    }
}
