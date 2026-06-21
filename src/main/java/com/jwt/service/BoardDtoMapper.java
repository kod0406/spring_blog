package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.entity.Board;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoardDtoMapper {
    private final MarkdownService markdownService;
    private final AuthorizationService authorizationService;

    public BoardDto.Response toResponse(Board board) {
        return new BoardDto.Response(board, markdownService.render(board.getContentMarkdown()));
    }

    public BoardDto.Response toListResponse(Board board, User viewer) {
        if (isPrivateCategory(board) && !authorizationService.isAdmin(viewer)) {
            return BoardDto.Response.masked(board);
        }
        return toResponse(board);
    }

    private boolean isPrivateCategory(Board board) {
        return board.getCategory() != null && board.getCategory().getVisibility() == CategoryVisibility.PRIVATE;
    }
}
