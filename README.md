# chokchok

FastAPI가 분석한 로그 이상탐지 리포트를 저장·조회하는 Spring Boot 백엔드.
FastAPI(분석 엔진) → Spring(저장/조회 API) → 프론트엔드 구조에서 가운데 저장·조회 계층을 담당한다.

## 기술 스택

- Java 21, Spring Boot 4.0.7 (Gradle)
- Spring Security + OAuth2 Resource Server (JWT, HS256 대칭키)
- Spring Data JPA + MySQL 8.4
- Flyway (스키마 마이그레이션, `src/main/resources/db/migration`)

## 사전 요구사항

- **Docker Desktop** (권장 실행 경로 — DB + 앱을 한 번에 기동)
- 또는 로컬 개발용으로 JDK 21 + MySQL 8.4

## 빠른 실행 (Docker Compose)

DB(MySQL)와 앱을 함께 띄우는 가장 간단한 경로.

```bash
# 1. 환경변수 파일 준비
cp .env.example .env

# 2. .env 값 채우기 (아래 두 시크릿은 반드시 교체 — 미설정 시 기동 실패)
#    JWT_SECRET            : HS256 서명키, 최소 32바이트
#    INTERNAL_SHARED_SECRET: FastAPI↔Spring 내부 API 공유 시크릿
#    생성 예: openssl rand -base64 36

# 3. 빌드 + 기동
docker compose up --build
```

- 앱: http://localhost:8080
- DB(GUI 접속용): localhost:3307 (컨테이너 내부는 3306)
- 스키마는 기동 시 Flyway가 자동 적용(V1, V2). 별도 DDL 실행 불필요.

중지: `docker compose down` (데이터 포함 초기화는 `docker compose down -v`)

## 로컬 개발 실행 (gradlew)

앱을 IDE/gradle로 직접 돌리고 DB만 컨테이너로 쓰는 경우.

```bash
# 1. DB만 컨테이너로 기동
docker compose up -d db

# 2. 필수 환경변수 설정 후 앱 실행 (compose db는 3307로 노출됨)
export DB_HOST=localhost DB_PORT=3307
export JWT_SECRET="로컬용-랜덤-최소-32바이트-문자열"
export INTERNAL_SHARED_SECRET="로컬용-공유-시크릿"
./gradlew bootRun
```

> Windows PowerShell은 `export` 대신 `$env:JWT_SECRET="..."` 형식 사용.

## 환경변수

| 변수 | 필수 | 설명 |
|---|---|---|
| `JWT_SECRET` | ✅ | JWT 서명/검증 키 (HS256, 최소 32바이트). 미설정 시 기동 실패 |
| `INTERNAL_SHARED_SECRET` | ✅ | 내부 API 공유 시크릿. 미설정 시 기동 실패 |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | | DB 접속 정보 (기본값 chokchok) |
| `DB_ROOT_PASSWORD` | | MySQL root 비밀번호 (compose 전용) |
| `APP_CORS_ORIGINS` | | CORS 허용 origin (기본 `http://localhost:5173`) |

`.env`는 gitignore 처리됨 — 실제 값은 커밋 금지. 템플릿은 `.env.example` 참고.

## API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/login` | 공개 | 로그인 → access/refresh 토큰 발급 |
| POST | `/api/auth/refresh` | 공개 | refresh 토큰으로 access 재발급 |
| GET | `/api/auth/me` | JWT | 현재 사용자 정보 |
| GET | `/api/reports` | JWT | 리포트 목록 (회사별 스코프) |
| GET | `/api/reports/{id}` | JWT | 리포트 상세 |
| GET | `/api/dashboard` | JWT | 대시보드 집계 |
| POST | `/api/internal/reports` | 공유 시크릿 | FastAPI→Spring 리포트 수집 (`X-Internal-Secret` 헤더) |

- 일반 API는 `Authorization: Bearer <access-token>` 필요.
- `/api/internal/**`는 사용자 JWT가 아니라 서버 간 공유 시크릿으로 인증한다. FastAPI가 `X-Internal-Secret` 헤더에 `INTERNAL_SHARED_SECRET`을 실어 호출.
  - ⚠️ **현재 이 검증은 임시 비활성화 상태**(FastAPI 송신측 헤더 미구현). `SecurityConfig`의 `InternalSecretFilter` 등록이 주석 처리돼 있어 지금은 헤더 없이도 호출된다. FastAPI 연동 완료 후 주석 해제 필요.
- 권한 없는 리소스(타 회사 리포트 등)는 존재를 숨기기 위해 403이 아닌 **404**로 응답한다.

## 테스트

```bash
./gradlew test
```

대부분 단위 테스트는 Docker 불필요. 단 `@SpringBootTest`(컨텍스트 로딩) 2개는 Testcontainers로 MySQL을 자체 기동하므로 **Docker 실행 중**이어야 통과한다.
