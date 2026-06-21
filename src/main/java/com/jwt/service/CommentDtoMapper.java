package com.jwt.service;

import com.jwt.dto.CommentDto;
import com.jwt.entity.Comment;
import org.springframework.stereotype.Component;

@Component
public class CommentDtoMapper {
    public CommentDto.Response toResponse(Comment comment, int depth) {
        return new CommentDto.Response(comment, depth);
    }
}
