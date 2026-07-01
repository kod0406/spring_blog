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
- 이메일 기반 회원가입과 로그인, 선택적 회원가입 이메일 인증
- 이메일 인증 코드 기반 계정 복구와 비밀번호 재설정

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

Redis는 refresh token과 이메일 인증 코드 저장소로 사용됩니다. Refresh JWT 원문은 저장하지 않고 SHA-256 hash를 `refresh_token:{userId}`에 TTL과 함께 저장합니다. 갱신 시 Lua compare-and-set으로 현재 hash가 일치할 때만 새 refresh token으로 원자적 rotation합니다. 사용자당 활성 refresh 세션은 1개이므로 새 로그인은 이전 refresh token을 무효화합니다.

Refresh token 저장소는 보안 상태의 일관성을 위해 인메모리 fallback을 사용하지 않습니다. Redis 장애 시 로그인·갱신·폐기는 `503 Service Unavailable`로 실패합니다. 이메일 인증 코드는 단일 인스턴스 개발 편의를 위한 인메모리 fallback을 유지하지만, 운영에서는 Redis 연결과 장애 감시를 필수로 구성해야 합니다.

```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

## JWT 세션과 쿠키

- access token: 기본 5분, `type=access`, 요청 인증에만 사용
- refresh token: 기본 6시간, `type=refresh`, Redis 비교와 재발급에만 사용
- `POST /api/user/token/refresh`: refresh cookie 검증, Redis hash 비교, access/refresh token 동시 rotation
- 로그아웃과 비밀번호 재설정: Redis refresh 세션 폐기
- `PENDING`, `REJECTED` 사용자의 기존 JWT: 인증 단계에서 거부

기존 `type` claim이 없는 JWT는 이 버전 배포 후 사용할 수 없으므로 사용자는 한 번 다시 로그인해야 합니다. 보호된 Thymeleaf 화면에서 access token이 만료되면 로그인 화면이 refresh API를 한 번 호출하고 성공 시 원래 내부 경로로 돌아갑니다.

운영 환경에서는 기본 개발 secret을 사용하지 말고 다음 값을 반드시 설정합니다.

```bash
JWT_SECRET=32바이트-이상의-충분히-긴-무작위-secret
JWT_COOKIE_SECURE=true
JWT_COOKIE_SAME_SITE=Lax
```

`JWT_COOKIE_SECURE=false`는 로컬 HTTP 개발 기본값입니다. HTTPS 운영에서는 반드시 `true`여야 합니다.

## 메일과 계정 복구

회원가입 이메일 인증은 기본값이 비활성화되어 있습니다. 운영에서 강제하려면 SMTP 설정과 함께 `EMAIL_VERIFICATION_ENABLED=true`를 지정합니다.

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
EMAIL_VERIFICATION_ENABLED=true
```

비밀번호 재설정은 설정값과 무관하게 이메일 인증 코드를 반드시 요구합니다. 로그인 식별자가 이메일 하나이므로 별도 사용자명 찾기 대신 `/reset-password`의 계정 복구 흐름을 사용합니다. 계정 존재 여부 노출을 막기 위해 등록된 이메일과 미등록 이메일의 인증 코드 요청 응답은 같습니다.

회원가입 코드와 비밀번호 재설정 코드는 Redis key namespace가 분리되어 서로 대신 사용할 수 없습니다. 인증 코드는 5분 후 만료되고 성공 시 즉시 삭제됩니다.

## 파일 업로드와 OCI

이미지와 영상은 서로 다른 비공개 OCI Object Storage 버킷에 저장합니다. 브라우저에는 OCI 객체 URL이나 PAR URL을 전달하지 않고 애플리케이션의 `/media/{mediaId}` 경로만 제공합니다. 서버가 게시글의 PUBLIC/PRIVATE, published, draft 정책을 검사한 뒤 OCI 객체를 스트리밍합니다.

파일 제한과 저장 정책:

- 이미지 5MB: JPEG, PNG, WebP, GIF
- 동영상 60MB: MP4, WebM
- Apache Tika로 실제 파일 형식을 검사하며 클라이언트 Content-Type만 신뢰하지 않음
- object key는 원본 파일명을 포함하지 않는 UUID 기반 값
- 업로드와 조회는 `InputStream` 기반이며 전체 파일을 `byte[]`로 적재하지 않음
- 동영상은 단일 HTTP Range와 `206`, `Content-Range`, `Accept-Ranges`를 지원

### 운영: Instance Principal

OCI ARM Compute 운영 환경의 기본 인증 방식은 `instance-principal`입니다. Compute instance가 속한 dynamic group과 두 비공개 버킷에 대한 Object Storage IAM policy는 OCI에서 별도로 구성해야 합니다. 애플리케이션이나 저장소에 API signing key를 배치하지 않습니다.

```bash
OCI_AUTH_MODE=instance-principal
OCI_REGION=사용자 직접 입력
OCI_NAMESPACE=사용자 직접 입력
OCI_IMAGE_BUCKET=사용자 직접 입력
OCI_VIDEO_BUCKET=사용자 직접 입력
```

### 로컬: OCI config file

로컬에서는 프로젝트 외부의 `~/.oci/config`와 해당 config가 가리키는 PEM API signing key를 사용합니다. 실제 값은 환경변수 또는 외부 properties에만 저장합니다.

```powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION='file:C:/secure/oci-local.properties'
```

외부 `oci-local.properties` 형식:

```properties
oci.object-storage.auth-mode=config-file
oci.object-storage.config-file=외부 OCI config 경로
oci.object-storage.profile=DEFAULT
oci.object-storage.region=사용자 직접 입력
oci.object-storage.namespace=사용자 직접 입력
oci.object-storage.image-bucket=사용자 직접 입력
oci.object-storage.video-bucket=사용자 직접 입력
```

외부 properties, `.oci`, PEM, private key는 Git에 추가하지 않습니다. 배포 서버 접속에 사용하는 **SSH private key**와 OCI API 요청 서명에 사용하는 **OCI API signing key**는 목적과 등록 위치가 다른 별도 키입니다. SSH 키를 OCI SDK config의 `key_file`로 사용하거나 API signing key를 SSH 배포 키로 사용하지 않습니다.

### 초안과 미디어 수명주기

- 새 글은 먼저 `draft=true`, `published=false` 초안으로 생성
- 업로드는 `postId`가 필수이며 초안 또는 기존 글에 연결
- `임시 저장`은 초안을 유지하고 `게시/저장`은 `draft=false`로 전환
- PUBLIC 목록·검색·상세에서 초안 제외
- 본문에서 제거된 미디어는 `ORPHAN`으로 전환
- 24시간 지난 ORPHAN과 미완성 초안을 scheduler가 정리
- OCI 삭제가 실패하면 DB row를 남겨 다음 실행에서 재시도
- 기존 미연결 미디어 row는 자동 삭제하지 않음

미디어 조회 정책:

- PUBLIC + `published=true` + 비초안: 익명 조회 가능
- PRIVATE, `published=false`, draft: ADMIN만 조회 가능
- 권한 없음, 연결 없음, ORPHAN: 404

## 주요 경로

- `/posts`: 공개 글 목록
- `/posts/{id}`: 글 상세
- `/login`: 로그인
- `/register`: 회원가입과 회원가입 이메일 인증
- `/reset-password`: 계정 복구와 비밀번호 재설정
- `/admin`: 관리자 대시보드
- `/admin/posts`: 관리자 글 관리
- `/admin/categories`: 글머리 관리
- `/admin/users`: 회원 관리
- `/admin/comments`: 댓글 관리
- `/api/posts`: 공개 글 API
- `/api/categories`: 공개 글머리 API
- `POST /api/user/token/refresh`: JWT 갱신과 refresh token rotation
- `POST /api/admin/posts/drafts`: 관리자 초안 생성
- `PUT /api/admin/posts/{id}/draft`: 관리자 임시 저장
- `POST /api/admin/uploads/images`: 이미지 업로드, `postId` 필수
- `POST /api/admin/uploads/videos`: 동영상 업로드, `postId` 필수
- `GET /media/{mediaId}`: 권한 검사 후 미디어 스트리밍
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
- API/Web 로그인과 회원가입, PENDING 회원 로그인 차단
- 회원가입 이메일 인증 코드 검증
- 계정 존재 여부를 노출하지 않는 복구 요청
- 비밀번호 재설정 코드 용도 분리, 코드 없는 변경 차단, refresh token 폐기
- access/refresh token type 교차 사용 차단
- Redis refresh token hash 저장, TTL, 원자적 rotation과 재사용 차단
- 이미지·동영상 OCI 버킷 분리와 InputStream 업로드
- MIME 위조·빈 파일·크기 초과 거부
- PUBLIC/PRIVATE/unpublished/draft/ORPHAN 미디어 조회 정책
- 동영상 Range 조회와 206 응답
- 초안 임시 저장·최종 게시·공개 조회 제외
- ORPHAN 24시간 정리와 OCI 삭제 실패 재시도

실제 Redis 통합 테스트는 Testcontainers의 `redis:7.4-alpine`을 사용합니다. Docker daemon이 없으면 해당 테스트 2개는 skip되며 나머지 단위·통합 테스트는 계속 실행됩니다.

실제 OCI 통합 테스트는 기본 `test`와 `build`에서 제외됩니다. 외부 properties와 OCI 권한을 사용해 수동으로만 실행합니다.

```bash
./gradlew ociIntegrationTest
```

## OCI ARM 블루·그린 배포

GitHub Actions 워크플로는 `master` 브랜치에 push할 때만 실행됩니다. 일반 GitHub 호스팅 러너는 AMD64이므로 Buildx로 `linux/arm64` 이미지를 명시적으로 빌드하고 OCI ARM Compute로 전송합니다. OCI ARM 인스턴스에서 직접 `docker build .`을 실행하는 경우에는 호스트와 같은 ARM64 이미지가 만들어집니다.

필수 GitHub Actions secret:

- `OCI_DEPLOY_SSH_KEY`: Compute 접속 전용 SSH private key
- `OCI_SSH_KNOWN_HOSTS`: `ssh-keyscan`으로 사전에 확인한 서버 host key
- `OCI_HOST`: Compute의 접속 주소
- `OCI_USER`: 배포 계정명

서버에는 배포 전에 다음 파일을 준비합니다. 실제 인증값은 Git 저장소나 Actions 로그에 넣지 않습니다.

- `/opt/jwt-blog/config/application-production.properties`
- `/opt/jwt-blog/config/redis-password`
- `/etc/nginx/conf.d/jwt-blog.target`

외부 application properties에는 MySQL, JWT, 관리자 bootstrap, 메일, OCI 버킷 설정과 함께 다음 Redis 연결값을 지정합니다. `spring.data.redis.password`는 `/opt/jwt-blog/config/redis-password`의 내용과 같아야 합니다.

```properties
spring.data.redis.host=jwt-blog-redis
spring.data.redis.port=6379
spring.data.redis.password=사용자_직접_입력
```

Nginx는 전환 대상 파일을 upstream 안에서 include합니다. 최초 대상 파일에는 `server 127.0.0.1:18080;`을 기록합니다.

```nginx
upstream jwt_blog {
    include /etc/nginx/conf.d/jwt-blog.target;
}

server {
    listen 443 ssl;
    server_name example.com;

    location / {
        proxy_pass http://jwt_blog;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

배포 스크립트는 새 색상 컨테이너의 API 응답을 확인한 뒤 Nginx를 전환합니다. 실패하면 새 컨테이너를 제거하고, 전환 이후 실패한 경우 이전 Nginx 대상을 복원합니다. 성공하면 이전 애플리케이션 컨테이너는 제거하지만 직전 Docker 이미지는 롤백을 위해 유지합니다. 이미지 전체 자동 정리는 수행하지 않습니다.

Redis는 별도 단일 컨테이너와 영속 볼륨을 사용하므로 애플리케이션 blue/green 교체에 포함되지 않습니다. 운영 전에는 Docker, Nginx, `curl`, `flock`, Redis 데이터 디렉터리 권한, OCI Instance Principal의 Object Storage IAM 권한을 확인해야 합니다.