package com.jwt.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@SequenceGenerator(name = "board_seq", sequenceName = "board_seq", allocationSize = 1)
@Data
public class Board {
   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "board_seq")
    private long boardId;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Lob
    @Column
    private String contentMarkdown;

    @Column(nullable = true)
    private Boolean published = true;

    @Column(nullable = true)
    private Boolean draft = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @PrePersist
    void created() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (published == null) {
            published = true;
        }
        if (draft == null) {
            draft = false;
        }
        if (contentMarkdown == null) {
            contentMarkdown = content;
        }
    }

    @PreUpdate
    void updated() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isDraftPost() {
        return Boolean.TRUE.equals(draft);
    }
}
