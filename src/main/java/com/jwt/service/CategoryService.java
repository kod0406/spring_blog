package com.jwt.service;

import com.jwt.dto.CategoryDto;
import com.jwt.entity.Category;
import com.jwt.entity.User;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BoardRepository boardRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<CategoryDto.Response> getActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAscDisplayNameAsc()
                .stream()
                .map(CategoryDto.Response::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto.Response> getAllCategories(User user) {
        authorizationService.requireAdmin(user);
        return categoryRepository.findAllByOrderBySortOrderAscDisplayNameAsc()
                .stream()
                .map(CategoryDto.Response::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public Category findActiveByKey(String key) {
        return categoryRepository.findByKey(key)
                .filter(category -> Boolean.TRUE.equals(category.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("글머리를 찾을 수 없습니다."));
    }

    @Transactional
    public CategoryDto.Response create(CategoryDto.Request request, User user) {
        authorizationService.requireAdmin(user);
        validateCreateRequest(request);

        String key = request.getKey().trim();
        if (categoryRepository.existsByKey(key)) {
            throw new IllegalArgumentException("이미 존재하는 글머리 key입니다.");
        }

        Category category = new Category();
        category.setKey(key);
        applyMutableFields(category, request);
        return new CategoryDto.Response(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto.Response update(Long categoryId, CategoryDto.Request request, User user) {
        authorizationService.requireAdmin(user);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("글머리를 찾을 수 없습니다."));

        applyMutableFields(category, request);
        return new CategoryDto.Response(category);
    }

    @Transactional
    public void delete(Long categoryId, User user) {
        authorizationService.requireAdmin(user);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("글머리를 찾을 수 없습니다."));

        boardRepository.findAll().stream()
                .filter(board -> board.getCategory() != null && board.getCategory().getCategoryId().equals(categoryId))
                .forEach(board -> board.setCategory(null));
        categoryRepository.delete(category);
    }

    private void validateCreateRequest(CategoryDto.Request request) {
        if (request == null || request.getKey() == null || request.getKey().trim().isEmpty()) {
            throw new IllegalArgumentException("글머리 key를 입력해 주세요.");
        }
        if (!request.getKey().matches("[a-z0-9][a-z0-9-]{1,78}[a-z0-9]")) {
            throw new IllegalArgumentException("글머리 key는 영문 소문자, 숫자, 하이픈만 사용할 수 있습니다.");
        }
        if (request.getDisplayName() == null || request.getDisplayName().trim().isEmpty()) {
            throw new IllegalArgumentException("표시 이름을 입력해 주세요.");
        }
    }

    private void applyMutableFields(Category category, CategoryDto.Request request) {
        if (request.getDisplayName() != null && !request.getDisplayName().trim().isEmpty()) {
            category.setDisplayName(request.getDisplayName().trim());
        }
        category.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
    }
}
