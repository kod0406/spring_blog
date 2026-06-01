package com.jwt.repository;

import com.jwt.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByKey(String key);
    boolean existsByKey(String key);
    List<Category> findAllByActiveTrueOrderBySortOrderAscDisplayNameAsc();
    List<Category> findAllByOrderBySortOrderAscDisplayNameAsc();
}
