package com.jwt.dto;

import com.jwt.entity.Category;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CategoryDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {
        private String key;
        private String displayName;
        private String description;
        private Integer sortOrder;
        private Boolean active;
    }

    @Getter
    public static class Response {
        private final Long categoryId;
        private final String key;
        private final String displayName;
        private final String description;
        private final Integer sortOrder;
        private final Boolean active;

        public Response(Category category) {
            this.categoryId = category.getCategoryId();
            this.key = category.getKey();
            this.displayName = category.getDisplayName();
            this.description = category.getDescription();
            this.sortOrder = category.getSortOrder();
            this.active = category.getActive();
        }
    }
}
