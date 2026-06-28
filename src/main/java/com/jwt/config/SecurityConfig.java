package com.jwt.config;

import com.jwt.jwt.JwtAuthenticationFilter;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${jwt.access-cookie-name}")
    private String accessCookieName;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userRepository, accessCookieName);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(401, "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(403, "접근 권한이 없습니다."))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityPaths.ADMIN_API_PATTERNS).hasRole("ADMIN")
                        .requestMatchers(SecurityPaths.PUBLIC_API_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, SecurityPaths.PUBLIC_GET_API_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/posts/*/comments",
                                "/api/comments/*/replies"
                        ).authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/comments/*").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            String target = request.getRequestURI();
                            if (request.getQueryString() != null) {
                                target += "?" + request.getQueryString();
                            }
                            response.sendRedirect("/login?returnUrl=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendRedirect("/login"))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityPaths.PUBLIC_WEB_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, SecurityPaths.PUBLIC_GET_WEB_PATTERNS).permitAll()
                        .requestMatchers(SecurityPaths.ADMIN_WEB_PATTERNS).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
