package com.jwt.repository;

import com.jwt.entity.Board;
import com.jwt.entity.Category;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long>, JpaSpecificationExecutor<Board> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Board b set b.category = null where b.category = :category")
    int clearCategory(@Param("category") Category category);
}
