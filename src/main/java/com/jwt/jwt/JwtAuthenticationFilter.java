package com.jwt.jwt;

import com.jwt.config.SecurityPaths;
import com.jwt.entity.User;
import com.jwt.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final String accessCookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        boolean publicPath = isPublicPath(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtTokenProvider.validateToken(token)) {
            SecurityContextHolder.clearContext();
            if (publicPath) {
                filterChain.doFilter(request, response);
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 JWT 토큰입니다.");
            return;
        }

        User user = findUser(token);
        if (user == null) {
            SecurityContextHolder.clearContext();
            if (publicPath) {
                filterChain.doFilter(request, response);
                return;
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "토큰 사용자를 찾을 수 없습니다.");
            return;
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> accessCookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private User findUser(String token) {
        String userId = jwtTokenProvider.getClaim(token, "sub");
        if (userId == null) {
            return null;
        }

        try {
            return userRepository.findById(Long.parseLong(userId)).orElse(null);
        } catch (NumberFormatException e) {
            log.warn("JWT subject is not a numeric user id. sub={}", userId);
            return null;
        }
    }

    private boolean isPublicPath(HttpServletRequest request) {
        return SecurityPaths.isPublic(request);
    }
}
