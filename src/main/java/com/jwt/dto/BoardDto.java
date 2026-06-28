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
        private final String displayTitle;
        private final String content;
        private final String renderedContent;
        private final String categoryKey;
        private final String categoryDisplayName;
        private final CategoryVisibility categoryVisibility;
        private final Boolean privatePost;
        private final Boolean published;
        private final Boolean draft;
        private final Boolean masked;
        private final Boolean readable;
        private final Long authorId;
        private final String authorName;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public Response(Board board) {
            this(board, board.getContent());
        }

        public Response(Board board, String renderedContent) {
            this(board, renderedContent, false);
        }

        private Response(Board board, String renderedContent, boolean masked) {
            this.masked = masked;
            this.readable = !masked;

            if (masked) {
                this.postId = null;
                this.title = "(권한 없음)";
                this.displayTitle = "(권한 없음)";
                this.content = null;
                this.renderedContent = null;
                this.categoryKey = null;
                this.categoryDisplayName = null;
                this.categoryVisibility = null;
                this.privatePost = null;
                this.published = null;
                this.draft = null;
                this.authorId = null;
                this.authorName = null;
                this.createdAt = null;
                this.updatedAt = null;
                return;
            }

            this.postId = board.getBoardId();
            this.title = board.getTitle();
            this.displayTitle = board.getTitle();
            this.content = board.getContentMarkdown() != null ? board.getContentMarkdown() : board.getContent();
            this.renderedContent = renderedContent;
            this.categoryKey = board.getCategory() != null ? board.getCategory().getKey() : null;
            this.categoryDisplayName = board.getCategory() != null ? board.getCategory().getDisplayName() : "미분류";
            this.categoryVisibility = board.getCategory() != null && board.getCategory().getVisibility() != null
                    ? board.getCategory().getVisibility()
                    : CategoryVisibility.PUBLIC;
            this.privatePost = this.categoryVisibility == CategoryVisibility.PRIVATE;
            this.published = board.getPublished() == null || board.getPublished();
            this.draft = board.isDraftPost();
            this.authorId = board.getUser() != null ? board.getUser().getUserId() : null;
            this.authorName = board.getUser() != null ? board.getUser().getName() : null;
            this.createdAt = board.getCreatedAt();
            this.updatedAt = board.getUpdatedAt();
        }

        public static Response masked(Board board) {
            return new Response(board, null, true);
        }

        public Long getBoardId() {
            return postId;
        }
    }
}
