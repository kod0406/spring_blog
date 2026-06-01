package com.jwt.dto;

import com.jwt.entity.Comment;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CommentDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private String content;
    }

    @Getter
    public static class Response {
        private final Long commentId;
        private final Long postId;
        private final Long parentId;
        private final Long authorId;
        private final String authorName;
        private final String content;
        private final boolean deleted;
        private final int depth;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        private final List<Response> children = new ArrayList<>();

        public Response(Comment comment, int depth) {
            this.commentId = comment.getCommentId();
            this.postId = comment.getPost() != null ? comment.getPost().getBoardId() : null;
            this.parentId = comment.getParent() != null ? comment.getParent().getCommentId() : null;
            this.authorId = comment.getAuthor().getUserId();
            this.authorName = comment.getAuthor().getName();
            this.deleted = Boolean.TRUE.equals(comment.getDeleted());
            this.content = this.deleted ? "삭제된 댓글입니다." : comment.getContent();
            this.depth = depth;
            this.createdAt = comment.getCreatedAt();
            this.updatedAt = comment.getUpdatedAt();
        }
    }
}
