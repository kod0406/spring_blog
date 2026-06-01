package com.jwt.repository;

import com.jwt.entity.Board;
import com.jwt.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long> {
    Page<Board> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select b from Board b where b.published = true or b.published is null order by b.createdAt desc")
    Page<Board> findPublicPosts(Pageable pageable);

    @Query("select b from Board b where b.category = :category and (b.published = true or b.published is null) order by b.createdAt desc")
    Page<Board> findPublicPostsByCategory(@Param("category") Category category, Pageable pageable);

    long countByCategory(Category category);
}
