# 1인 관리자 블로그

Spring Boot, Thymeleaf, JWT 쿠키 인증을 사용하는 개인 관리자 블로그입니다. 관리자는 `ADMIN` 하나만 사용하고, 일반 회원은 관리자 승인 후 공개 글에 댓글과 답글을 작성할 수 있습니다.

## 주요 기능

- 관리자 전용 글 작성, 수정, 삭제
- 글머리 속성: `PUBLIC`, `PRIVATE`
- `PUBLIC` 글머리 글과 미분류 공개 글은 공개 목록과 상세에 노출
- `PRIVATE` 글머리 글은 관리자만 목록, 상세, 댓글 조회 가능
- `published=false` 글은 관리자만 조회 가능
- 승인된 `ACTIVE` 회원은 공개 글에 댓글과 답글 작성 가능
- `PRIVATE` 글 댓글과 답글은 관리자만 작성 가능
- 댓글 삭제는 소프트 삭제: row 유지, `deleted=true`, `content=""`, 응답 문구는 `삭제된 댓글입니다.`
- 관리자 화면: 대시보드, 글 관리, 글머리 관리, 회원 승인/거절, 댓글 관리
- 관리자 API는 SecurityConfig와 서비스 레이어에서 모두 ADMIN 권한 검증

## 권한 모델

역할은 `ADMIN`, `USER`만 사용합니다.

기존 DB에 `OWNER` role 값이 남아 있으면 앱 시작 시 `ADMIN`으로 정규화합니다. `OWNER_EMAIL`, `OWNER_PASSWORD`, `OWNER_NAME` 환경 변수는 하위 호환 fallback으로만 읽고, 신규 설정은 `ADMIN_*`를 사용합니다.

## 로컬 실행

필수 조건:

- JDK 17
- Gradle Wrapper 사용

빌드:

```bash
./gradlew build
```

Windows에서 JDK 경로를 직접 지정해야 하는 경우:

```powershell
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain build
```

실행 예시:

```bash
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD=change-me \
ADMIN_NAME=Admin \
./gradlew bootRun
```

기본 서버 포트는 `8080`입니다.

## 관리자 Bootstrap

앱 시작 시 `ADMIN` 계정이 없고 아래 값이 설정되어 있으면 관리자 계정을 자동 생성합니다.

- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `ADMIN_NAME`

하위 호환:

- `OWNER_EMAIL`
- `OWNER_PASSWORD`
- `OWNER_NAME`

`ADMIN_*` 값이 우선이며, 비어 있을 때만 `OWNER_*`를 fallback으로 사용합니다.

## 데이터베이스

기본값은 로컬 H2 파일 DB입니다.

```yaml
DB_DRIVER=org.h2.Driver
DB_URL=jdbc:h2:file:./data/jwt-blog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
DB_USERNAME=sa
DB_PASSWORD=
```

MySQL을 사용할 때는 다음 값을 환경 변수로 지정합니다.

```bash
DB_DRIVER=com.mysql.cj.jdbc.Driver
DB_URL=jdbc:mysql://localhost:3306/jwt_blog?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
DB_USERNAME=...
DB_PASSWORD=...
JPA_DIALECT=org.hibernate.dialect.MySQLDialect
```

## Redis

Redis는 refresh token 저장소로 사용됩니다. 로컬 Redis가 없거나 연결에 실패하면 `TokenRedisService`가 인메모리 fallback으로 동작합니다. 운영 환경에서는 Redis 연결을 명확히 설정하는 것을 권장합니다.

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

## 파일 업로드와 OCI

이미지와 영상 업로드 API는 관리자 전용으로 준비되어 있습니다. 현재 OCI Object Storage 실제 SDK 인증키 연결은 이 작업 범위에 포함하지 않았습니다.

`OCI_OBJECT_STORAGE_ENABLED=false`가 기본값이며, 이 상태에서는 로컬 fallback URL을 사용합니다. 실제 OCI 연동에는 별도 SDK 설정, 인증키, 버킷 권한 구성이 필요합니다.

## 주요 경로

- `/posts`: 공개 글 목록
- `/posts/{id}`: 글 상세
- `/admin`: 관리자 대시보드
- `/admin/posts`: 관리자 글 관리
- `/admin/categories`: 글머리 관리
- `/admin/users`: 회원 관리
- `/admin/comments`: 댓글 관리
- `/api/posts`: 공개 글 API
- `/api/categories`: 공개 글머리 API
- `/api/admin/**`: 관리자 API

## 테스트

```bash
./gradlew build
```

주요 테스트 범위:

- ADMIN bootstrap과 legacy OWNER 정규화
- ADMIN/USER 권한 분리
- PUBLIC/PRIVATE 글머리 노출 정책
- PRIVATE 글과 `published=false` 글 접근 제어
- 공개/개인 글 댓글 권한
- 댓글 소프트 삭제와 대댓글 tree 유지
- 관리자 API 401/403/200 보안 정책
- 주요 Java/Thymeleaf/README 파일의 깨진 한글 문자열 마커 검사

## 리팩터링 구조 메모

- 권한 검증은 `AuthorizationService`를 중심으로 수행하며, 관리자 전용 서비스 메서드는 컨트롤러 보안 설정에만 의존하지 않습니다.
- 공개/관리자 경로 목록은 `SecurityPaths`에서 공유하여 `SecurityConfig`와 JWT filter의 허용 경로가 어긋나지 않게 유지합니다.
- 게시글 검색 조건은 `BoardSearchSpecificationFactory`, 게시글 DTO 변환은 `BoardDtoMapper`가 담당합니다.
- 관리자 댓글 조회는 전체 댓글을 메모리에서 필터링하지 않고 Repository Specification으로 필터링합니다.
- 공통 Thymeleaf navbar/flash 영역은 `templates/fragments/common.html` fragment를 사용합니다.
- 변경 후 기준 검증 명령은 `./gradlew test`와 최종 `./gradlew build`입니다.
