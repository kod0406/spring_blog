package com.jwt.config;


import com.jwt.jwt.JwtAuthenticationFilter;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults()) // 전역 CORS 설정을 사용하도록 명시
                .httpBasic(httpBasic -> httpBasic.disable()) // http basic auth 비활성화
                .csrf(csrf -> csrf.disable()) // csrf 비활성화
                .logout(logout -> logout.disable()) // 기본 로그아웃 비활성화
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 STATELESS 설정
                )
                .authorizeHttpRequests(authorize -> authorize
                        // -- CORS Preflight 요청은 항상 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                // -- Swagger UI & API Docs
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",

                                // -- 웹 페이지 접근 허용 (Thymeleaf)
                                "/",
                                "/login",
                                "/register",
                                "/reset-password",
                                "/reset-password/**",  // 비밀번호 재설정 관련 모든 경로 허용
                                "/logout",  // 사용자 정의 로그아웃 경로 허용
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/static/**",
                                "/email",

                                // -- 회원가입/로그인/로그아웃 등 공개 API
                                "/api/user/register",
                                "/api/user/login",
                                "/api/user/logout",
                                "/api/user/reset-password/**",  // API 비밀번호 재설정 경로 허용

                                // -- 이메일 발송 관련 API
                                "/email/**",
                                "/signup/email"
                        ).permitAll()
                        // 게시판은 인증 필요
                        .requestMatchers("/board/**").authenticated()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}