package com.jwt.dto;

import com.jwt.entity.Board;
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

        public Request(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    @Getter
    public static class Response {
        private final Long boardId;
        private final String title;
        private final String content;
        private final String authorName;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public Response(Board board) {
            this.boardId = board.getBoardId();
            this.title = board.getTitle();
            this.content = board.getContent();
            this.authorName = board.getUser().getName();
            this.createdAt = board.getCreatedAt();
            this.updatedAt = board.getUpdatedAt();
        }
    }
}

