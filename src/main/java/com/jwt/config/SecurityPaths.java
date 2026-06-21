package com.jwt.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

public final class SecurityPaths {
    public static final String[] PUBLIC_WEB_PATTERNS = {
            "/",
            "/login",
            "/error",
            "/register",
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

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private SecurityPaths() {
    }

    public static boolean isPublic(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (matches(uri, PUBLIC_WEB_PATTERNS) || matches(uri, PUBLIC_API_PATTERNS)) {
            return true;
        }

        if (HttpMethod.GET.matches(request.getMethod())) {
            return matches(uri, PUBLIC_GET_WEB_PATTERNS) || matches(uri, PUBLIC_GET_API_PATTERNS);
        }

        return false;
    }

    private static boolean matches(String uri, String[] patterns) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }
}
