package com.jwt.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jwt.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BoardRepository extends JpaRepository<Board, Long> {
    // 최신순(쓴 날짜순) 정렬 + 페이징
    Page<Board> findAllByOrderByCreatedAtDesc(Pageable pageable);
}