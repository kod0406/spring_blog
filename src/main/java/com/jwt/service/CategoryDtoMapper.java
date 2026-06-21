package com.jwt.service;

import com.jwt.dto.CategoryDto;
import com.jwt.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryDtoMapper {
    public CategoryDto.Response toResponse(Category category) {
        return new CategoryDto.Response(category);
    }
}
