package com.jwt.repository;

import com.jwt.entity.Board;
import com.jwt.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {
    List<Comment> findAllByPostOrderByCreatedAtAsc(Board post);
}
