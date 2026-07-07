# API 명세서

> 상태: **v0.1 초안** · 관련: [`schema.md`](./schema.md) / [`schema.sql`](./schema.sql)
> 범위: 기본 파이프라인만. 로그인 스킵, VizTab·AgentLogTab은 후순위.

## 0. 아키텍처 / 역할 분담

```
[엣지 수집기] ──POST /ingest──▶ [FastAPI] ──POST /api/internal/reports──▶ [Spring] ──▶ [MySQL]
                                  │ LLM RCA 분석 (raw 파싱은 여기서만)        ▲
                                  └──PATCH /api/internal/reports/{id}────────┘  (status·result)
[프론트(React)] ──GET /api/...──▶ [Spring]
```

| 컴포넌트 | 역할 |
| --- | --- |
| FastAPI | ingest 수신, Spring 저장 API 호출, LLM RCA 분석, 결과 PATCH |
| Spring | **DB 단독 소유** — 저장(internal API) + 프론트 조회 API. 분석·파싱 안 함 |
| 프론트 | Dashboard / Reports / ReportDetail(Summary·Cause·Impact·Action 탭) |

> ✅ **저장 주체 = Spring 확정 (A안, 7/7)** — SVC-01·D-001·슬라이스 1.5와 정합. WBS I8(에이전트↔Spring 연동) = §5 internal API 2종. FastAPI는 DB에 직접 접근하지 않는다. Spring이 먼저 완성되면 FastAPI 쪽은 internal API를 mock으로 치다가 실연동(7/7 회의 I8 당김 합의).

`report.status` 흐름: `OPEN`(Spring, 저장 직후) → `ANALYZING` → `DONE` / `FAILED` — 전환은 전부 **FastAPI가 PATCH로 요청, Spring이 기록**

## 1. 엔드포인트 한눈에

### 프론트 → Spring (베이스 `/api`, 인증 없음)

| Method | Path | 용도 | 화면 | 상태 | 상세 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/reports` | 리포트 목록 (status 필터·페이징) | Reports | ✅ 기본 | §4.1 |
| GET | `/api/reports/{id}` | 리포트 상세 (trigger·counts·result) | ReportDetail | ✅ 기본 | §4.2 |
| GET | `/api/dashboard` | status 집계 + 최근 리포트 | Dashboard | ✅ 기본 (화면 확정 후 조정) | §4.3 |
| GET | `/api/reports/{id}/logs·metrics·traces` | 3종 시각화 데이터 (ts 범위·service 필터) | VizTab | ⏸ 후순위 | — |
| GET | `/api/reports/{id}/agent-logs` | 에이전트 로그 (저장 테이블도 이때 추가) | AgentLogTab | ⏸ 후순위 | — |

### 수집기 → FastAPI

| Method | Path | 용도 | 상태 | 상세 |
| --- | --- | --- | --- | --- |
| POST | `/ingest` | 정규화 번들 수신 → Spring 저장 위임 + 분석 트리거 | ✅ 기본 | §3.1 |
| POST | `/reports/{id}/analyze` | 재분석 (FAILED 재시도) | ⏸ 후순위 | — |

### FastAPI → Spring (내부, 베이스 `/api/internal`) — WBS I8

| Method | Path | 용도 | 상태 | 상세 |
| --- | --- | --- | --- | --- |
| POST | `/api/internal/reports` | 번들 저장 (report+3종, 1 트랜잭션) | ✅ 기본 | §5.1 |
| PATCH | `/api/internal/reports/{id}` | status 전환·result 기록 | ✅ 기본 | §5.2 |

> internal 경로는 프론트 라우팅에서 제외만 하고 인증 없음 (내부 팀 도구, D-013).

## 2. 공통 데이터 형태

### ts 규약 (전 API 공통)

`yyyy-MM-dd HH:mm:ss.SSS` 문자열, **UTC 고정** (로그 원본 µs → ms 절사, DB `DATETIME(3)` 대응).

### 3종 아이템 (수집기 계약과 동일)

```json
{ "ts": "2025-11-04 00:01:57.490", "service": "media-service", "raw": { } }
```

### report.result (분석 결과 — ReportDetail 탭 4개와 1:1)

```json
{
  "summary": "media-service가 00:01:57에 종료되어 104초간 로그 침묵...",
  "cause":   { "service": "media-service", "description": "...", "evidence": ["log:1234", "..."] },
  "impact":  { "services": ["compose-post-service"], "description": "..." },
  "action":  ["media-service 재시작 정책 점검", "..."]
}
```

> 내부 구조는 LLM 출력에 따라 조정 가능. 탭 4개 = 최상위 키 4개 계약만 고정.

## 3. FastAPI 상세 (수집기 → FastAPI)

### 3.1 POST /ingest

수집기가 정규화 번들을 전달. FastAPI는 **바디를 그대로 `POST /api/internal/reports`(§5.1)로 위임**해 저장하고,
LLM 분석을 **비동기로 트리거**한 뒤 즉시 응답. (저장 트랜잭션은 Spring 몫)

**Request**

```json
{
  "bundle_id": "svc-kill-media-20251104-000111-a3f2",
  "title": "Svc_Kill_Media_20251104_000111",
  "window": { "from": "2025-11-04 00:00:00.000", "to": "2025-11-04 00:10:00.000" },
  "trigger": {
    "ts": "2025-11-04 00:01:57.490",
    "signal": "log_silence+start_log_x2",
    "services": ["media-service"]
  },
  "logs":    [ { "ts": "...", "service": "...", "raw": {} } ],
  "metrics": [ { "ts": "...", "service": "...", "raw": {} } ],
  "traces":  [ { "ts": "...", "service": "...", "raw": {} } ]
}
```

- `bundle_id`: 엣지가 생성하는 멱등 키 — 재시도(MVP-정의서 §7.3) 시 중복 report 방지 (`report.bundle_id` UNIQUE)
- `trigger`: 발화 신호 컨텍스트 — RCA의 핵심 입력(무슨 신호로, 어느 서비스가 연루됐나). ④ 번들 스키마(UC-06)의 "시각창·연루 서비스" 대응. `signal` 값 체계는 SDK 스키마(유경·예지)와 함께 확정
- 3종 배열은 **±Δ·Top-K로 제한된 번들** — 원천 전량 금지 (NFR-01·02)

**Response** `201`

```json
{ "report_id": 1 }
```

**에러**: `422` 스키마 위반(ts 형식·필수 필드), `409` 중복 `bundle_id`(Spring 응답 그대로 전달, 기존 `report_id` 포함), `502` Spring 저장 실패(report 미생성 — 엣지가 재시도)

> 분석 완료를 기다리지 않는다. 프론트는 status 폴링(Spring 조회)으로 DONE 확인.

## 4. Spring 상세 (프론트 → Spring)

**에러 공통 형태** (전 엔드포인트): `{ "error": { "code": "REPORT_NOT_FOUND", "message": "..." } }`

### 4.1 GET /api/reports — 목록 (Reports 페이지)

Query: `status`(선택), `page`(기본 0), `size`(기본 20)

```json
{
  "content": [
    { "id": 1, "title": "...", "status": "DONE",
      "windowFrom": "2025-11-04 00:00:00", "windowTo": "2025-11-04 00:10:00",
      "createdAt": "2026-07-07 10:00:00" }
  ],
  "totalElements": 42, "totalPages": 3, "page": 0
}
```

### 4.2 GET /api/reports/{id} — 상세 (ReportDetail)

목록 아이템 + `trigger` + `result`(§2 형태, 미완료면 `null`) + 3종 건수:

```json
{
  "id": 1, "title": "...", "status": "DONE",
  "windowFrom": "...", "windowTo": "...", "createdAt": "...",
  "trigger": { "ts": "...", "signal": "log_silence+start_log_x2", "services": ["media-service"] },
  "counts": { "logs": 842, "metrics": 240, "traces": 120 },
  "result": { "summary": "...", "cause": {}, "impact": {}, "action": [] }
}
```

`404` 없는 id. counts는 번들이 ±Δ·Top-K 제한이므로 수백~수천 수준이 정상 — 수십만이면 엣지 정제가 빠진 것.

### 4.3 GET /api/dashboard — 대시보드 요약

```json
{
  "statusCounts": { "OPEN": 1, "ANALYZING": 2, "DONE": 39, "FAILED": 0 },
  "recentReports": [ /* 목록 아이템 5건 */ ]
}
```

> 화면 확정 후 필드 조정. 목록 재사용으로 충분하면 이 엔드포인트 삭제.

## 5. Spring 내부 API 상세 (FastAPI → Spring) — WBS I8

### 5.1 POST /api/internal/reports — 번들 저장

요청 바디 = **`/ingest`(§3.1)와 동일 계약** (bundle_id·title·window·trigger·3종). report 생성 + 3종 insert를 **한 트랜잭션**으로.

**Response** `201` `{ "report_id": 1 }`

**에러**: `409` 중복 `bundle_id` — `{ "error": { "code": "DUPLICATE_BUNDLE", "message": "...", "reportId": 1 } }` (DB UNIQUE 제약이 최종 방어선), `422` 필수 필드·ts 형식 위반, `500` 트랜잭션 롤백(report 미생성)

### 5.2 PATCH /api/internal/reports/{id} — 상태 전환·결과 기록

```json
{ "status": "ANALYZING" }
{ "status": "DONE", "result": { "summary": "...", "cause": {}, "impact": {}, "action": [] } }
{ "status": "FAILED" }
```

**Response** `200` (갱신된 상세). **에러**: `404` 없는 id, `422` `DONE`인데 `result` 누락 / 허용되지 않는 status 값

> 상태 전이 검증은 최소만: `DONE` ⇒ `result` 필수. 역방향 전이 차단 같은 상태기계는 필요해지면 추가.

## 6. 7/8 오전 회의 확정 체크리스트

- [x] ~~저장 주체~~ → **Spring 확정 (A안, 7/7)** — §0·§5 반영 완료. 회의에서 공유만
- [ ] **ts 규약** `yyyy-MM-dd HH:mm:ss.SSS` UTC — SDK↔FastAPI 계약(유경·예지 오전 확정분)과 동일한지 맞추기
- [ ] **`trigger.signal` 값 체계** — SDK 트리거 구현(유경)과 함께 enum 확정 (예: `cpu_threshold_duration`, `log_absence`, `log_silence+start_log_x2`)
- [ ] **internal API 2종(§5) 계약을 가희와 확인** — I8 상대편. FastAPI가 mock으로 먼저 칠 수 있는 형태인지
- [ ] **혜림 내부용 API 정의서와의 관계** — 본 문서를 베이스로 공유 (중복 작성 방지, 회의에서 "별도 작성 시 혜림" 언급 건)
- [ ] `report.result` 4키 계약(summary·cause·impact·action)을 에이전트팀(예지·가희)이 산출 가능한지 확인
