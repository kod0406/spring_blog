package com.jwt.service;

import com.jwt.dto.CategoryDto;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryServiceTest {

    @Autowired
    CategoryService categoryService;

    @Autowired
    UserRepository userRepository;

    @Test
    void categoryDefaultsToPublicAndPrivateIsHiddenFromPublicList() {
        User admin = userRepository.save(user("category-default-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        CategoryDto.Request publicRequest = new CategoryDto.Request();
        publicRequest.setKey("default-public");
        publicRequest.setDisplayName("기본 공개");
        CategoryDto.Response publicCategory = categoryService.create(publicRequest, admin);

        CategoryDto.Request privateRequest = new CategoryDto.Request();
        privateRequest.setKey("hidden-private");
        privateRequest.setDisplayName("숨김 개인");
        privateRequest.setVisibility(CategoryVisibility.PRIVATE);
        CategoryDto.Response privateCategory = categoryService.create(privateRequest, admin);

        assertThat(publicCategory.getVisibility()).isEqualTo(CategoryVisibility.PUBLIC);
        assertThat(categoryService.getActiveCategories())
                .extracting(CategoryDto.Response::getKey)
                .contains(publicCategory.getKey())
                .doesNotContain(privateCategory.getKey());
        assertThat(categoryService.getAllCategories(admin))
                .extracting(CategoryDto.Response::getKey)
                .contains(publicCategory.getKey(), privateCategory.getKey());
    }

    private User user(String email, String name, UserRole role, UserStatus status) {
        return User.builder()
                .email(email)
                .name(name)
                .password("{noop}password")
                .role(role)
                .status(status)
                .build();
    }
}
