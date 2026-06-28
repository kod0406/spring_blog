package com.jwt.config;

public final class SecurityPaths {
    public static final String[] PUBLIC_WEB_PATTERNS = {
            "/",
            "/login",
            "/error",
            "/register",
            "/logout",
            "/reset-password",
            "/reset-password/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/static/**",
            "/h2-console/**",
            "/email",
            "/email/**",
            "/signup/email"
    };

    public static final String[] PUBLIC_API_PATTERNS = {
            "/api/user/register",
            "/api/user/login",
            "/api/user/logout",
            "/api/user/token/refresh",
            "/api/user/reset-password",
            "/api/user/reset-password/**"
    };

    public static final String[] PUBLIC_GET_API_PATTERNS = {
            "/api/categories",
            "/api/posts",
            "/api/board",
            "/api/posts/*",
            "/api/board/*",
            "/api/posts/*/comments"
    };

    public static final String[] PUBLIC_GET_WEB_PATTERNS = {
            "/posts",
            "/posts/{postId}",
            "/board",
            "/board/{postId}"
    };

    public static final String[] ADMIN_API_PATTERNS = {
            "/api/admin/**"
    };

    public static final String[] ADMIN_WEB_PATTERNS = {
            "/admin/**",
            "/posts/write",
            "/board/write",
            "/posts/**/edit",
            "/board/**/edit"
    };

    private SecurityPaths() {
    }
}
