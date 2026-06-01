package com.jwt.dto;

import com.jwt.entity.Board;
import com.jwt.entity.CategoryVisibility;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class BoardDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private String title;
        private String content;
        private String contentMarkdown;
        private String categoryKey;
        private Boolean published;

        public Request(String title, String content) {
            this.title = title;
            this.content = content;
            this.contentMarkdown = content;
        }
    }

    @Getter
    public static class Response {
        private final Long postId;
        private final String title;
        private final String content;
        private final String renderedContent;
        private final String categoryKey;
        private final String categoryDisplayName;
        private final CategoryVisibility categoryVisibility;
        private final Boolean privatePost;
        private final Boolean published;
        private final Long authorId;
        private final String authorName;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public Response(Board board) {
            this(board, board.getContent());
        }

        public Response(Board board, String renderedContent) {
            this.postId = board.getBoardId();
            this.title = board.getTitle();
            this.content = board.getContentMarkdown() != null ? board.getContentMarkdown() : board.getContent();
            this.renderedContent = renderedContent;
            this.categoryKey = board.getCategory() != null ? board.getCategory().getKey() : null;
            this.categoryDisplayName = board.getCategory() != null ? board.getCategory().getDisplayName() : "미분류";
            this.categoryVisibility = board.getCategory() != null && board.getCategory().getVisibility() != null
                    ? board.getCategory().getVisibility()
                    : CategoryVisibility.PUBLIC;
            this.privatePost = this.categoryVisibility == CategoryVisibility.PRIVATE;
            this.published = board.getPublished() == null || board.getPublished();
            this.authorId = board.getUser() != null ? board.getUser().getUserId() : null;
            this.authorName = board.getUser() != null ? board.getUser().getName() : null;
            this.createdAt = board.getCreatedAt();
            this.updatedAt = board.getUpdatedAt();
        }

        public Long getBoardId() {
            return postId;
        }
    }
}
