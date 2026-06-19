package com.jwt.dto;

import lombok.Getter;

@Getter
public class BoardSearchCondition {
    private final String visibility;
    private final String category;
    private final String keyword;
    private final String searchType;
    private final String sort;

    public BoardSearchCondition(String visibility, String category, String keyword, String searchType, String sort) {
        this.visibility = normalize(visibility);
        this.category = normalize(category);
        this.keyword = normalize(keyword);
        this.searchType = normalize(searchType);
        this.sort = normalize(sort);
    }

    public static BoardSearchCondition publicSearch(String category, String keyword, String searchType, String sort) {
        return new BoardSearchCondition(null, category, keyword, searchType, sort);
    }

    public static BoardSearchCondition adminSearch(String visibility, String category, String keyword, String searchType, String sort) {
        return new BoardSearchCondition(visibility, category, keyword, searchType, sort);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
