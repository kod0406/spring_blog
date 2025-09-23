         # JWT 인증 시스템 - 동아리 세션 프로젝트

Spring Boot 기반의 JWT(JSON Web Token) 인증 시스템 구현 프로젝트입니다.  
회원가입, 로그인, 로그아웃, 게시판 CRUD 기능을 포함하며, 동아리 세션을 위한 학습용 템플릿을 제공합니다.

## 📋 목차
- [프로젝트 개요](#-프로젝트-개요)
- [기술 스택](#-기술-스택)
- [주요 기능](#-주요-기능)
- [프로젝트 구조](#-프로젝트-구조)
- [프로젝트 실행 가이드](#-프로젝트-실행-가이드)
- [환경 변수 설정](#-환경-변수-설정-applicationyml)


## 🎯 프로젝트 개요

이 프로젝트는 JWT 기반 인증 시스템을 학습하기 위한 동아리 세션용 템플릿입니다.  
실제 구현이 필요한 부분은 TODO와 빈칸으로 남겨두어, 학습자가 직접 구현할 수 있도록 설계되었습니다.

### 학습 목표
- Spring Security와 JWT 토큰 기반 인증 이해
- Redis를 활용한 Refresh Token 관리
- RESTful API 설계 및 구현
- 쿠키 기반 토큰 저장 및 관리
- 비밀번호 암호화 (BCrypt)

## 🛠 기술 스택

- **Backend**: Spring Boot 3.x, Spring Security 6.x
- **Database**: MySQL, JPA/Hibernate
- **Cache**: Redis (Refresh Token 저장)
- **Template Engine**: Thymeleaf
- **Authentication**: JWT (JSON Web Token)
- **Password Encryption**: BCrypt
- **Build Tool**: Gradle

## ⚡ 주요 기능

### 인증 시스템
- [x] 회원가입 (이메일 중복 검사)
- [x] 로그인/로그아웃
- [x] JWT Access Token + Refresh Token
- [x] 비밀번호 재설정

### 게시판 시스템
- [x] 게시글 목록 조회 (페이징)
- [x] 게시글 작성/수정/삭제
- [x] 게시글 상세보기
- [x] 작성자 권한 검증

### 보안 기능
- [x] 비밀번호 BCrypt 암호화
- [x] JWT 토큰 쿠키 저장
- [x] CORS 설정
- [x] CSRF 비활성화 (JWT 사용)

## 📁 프로젝트 구조

```
src/main/java/com/jwt/
├── config/                 # 설정 클래스
│   ├── SecurityConfig.java    # Spring Security 설정
│   └── RedisConfig.java       # Redis 설정
├── controller/             # 컨트롤러
│   ├── WebController.java     # 웹 페이지 컨트롤러
│   ├── UserController.java    # 사용자 API 컨트롤러
│   └── BoardController.java   # 게시판 API 컨트롤러
├── dto/                    # 데이터 전송 객체
│   ├── RegistrationDto.java
│   ├── loginDto.java
│   └── BoardDto.java
├── entity/                 # JPA 엔티티
│   ├── User.java
│   └── Board.java
├── jwt/                    # JWT 관련 클래스
│   ├── JwtTokenProvider.java  # JWT 토큰 생성/검증
│   ├── JwtAuthenticationFilter.java # JWT 인증 필터
│   └── JwtCookieUtil.java     # JWT 쿠키 유틸리티
├── redis/                  # Redis 관련 클래스
│   └── TokenRedisService.java # 토큰 Redis 서비스
├── repository/             # JPA 리포지토리
│   ├── UserRepository.java
│   └── BoardRepository.java
└── service/                # 비즈니스 로직
    ├── UserService.java
    └── BoardService.java
```

## 🚀 프로젝트 실행 가이드

### 시스템 요구사항

* **JDK**: 17 이상
* **Gradle**: 7.x 이상

---

### 실행 방법

#### 1. 프로젝트 클론 및 설정

1. 프로젝트 클론:

   ```bash
   git clone https://github.com/your-username/jwt-auth-project.git
   cd jwt-auth-project
   ```

2. 환경 설정 파일 작성:
   - `src/main/resources/application.yml` 파일을 생성하거나 수정합니다.
   - 아래 "환경 변수 설정" 섹션을 참고하여 값을 입력하세요.

#### IDE에서 실행 (IntelliJ IDEA)

1. IntelliJ IDEA에서 프로젝트 열기
2. Gradle 프로젝트로 임포트
3. 메인 애플리케이션 클래스 (`JwtApplication.java`) 실행

---

## ⚙️ 환경 변수 설정 (`application.yml`)

백엔드 프로젝트의 `src/main/resources/` 경로에 `application.yml` 파일을 생성하고, 아래 예시를 참고하여 환경에 맞는 설정값을 입력해야 합니다.

<details>
<summary><strong>전체 환경 변수 예시 및 설명 보기</strong></summary>

```yaml
spring:
  application:
    name: JWT

  security:
    user:
      name: user                    # 기본 보안 사용자명
      password: 1234                # 기본 보안 비밀번호

  thymeleaf:
    cache: false                    # 개발 시 템플릿 캐시 비활성화

  jpa:
    hibernate:
      ddl-auto: update              # 테이블 자동 생성/업데이트 (create, update, validate, create-drop)

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://[RDS_ENDPOINT]/[RDS_TABLE_NAME]?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true
    username: [DB_USERNAME]         # 데이터베이스 사용자명
    password: [DB_PASSWORD]         # 데이터베이스 비밀번호

  data:
    redis:
      host: [REDIS_HOST]            # Redis 서버 주소
      port: 6379
      password: [REDIS_PASSWORD]    # Redis 비밀번호
      repositories:
        enabled: false

server:
  port: 8080                        # 서버 포트

jwt:
  secret: [JWT_SECRET_KEY]          # JWT 서명용 비밀키 (최소 32자 이상)
  refresh-Millis: 21600000          # 리프레시 토큰 만료시간 (밀리초)
  expiration-Millis: 300000         # 액세스 토큰 만료시간 (밀리초)
  access-cookie-name: jwt_token     # 액세스 토큰 쿠키명
  refresh-cookie-name: jwt_refresh_token # 리프레시 토큰 쿠키명
```
</details>

### 설정값 변경 가이드

1. **`[RDS_ENDPOINT]`**: AWS RDS MySQL 인스턴스의 엔드포인트를 입력
2. **`[DB_USERNAME]`, `[DB_PASSWORD]`**: 데이터베이스 접속 계정 정보
3. **`[REDIS_HOST]`, `[REDIS_PASSWORD]`**: Redis 서버 주소 및 비밀번호
4. **`[JWT_SECRET_KEY]`**: JWT 토큰 서명용 비밀키 (최소 32자 이상)

---