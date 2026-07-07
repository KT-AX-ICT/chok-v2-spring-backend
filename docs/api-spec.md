# API 명세서

> 상태: **v0.1 초안** · 관련: [`schema.md`](./schema.md) / [`schema.sql`](./schema.sql)
> 범위: 기본 파이프라인만. 로그인 스킵, VizTab·AgentLogTab은 후순위(§4).

## 0. 아키텍처 / 역할 분담

```
[엣지 수집기] ──POST /ingest──▶ [FastAPI]
                                  │ ① report + log/metric/trace insert (1 트랜잭션)
                                  │ ② LLM 분석 (raw 파싱은 여기서만)
                                  │ ③ report.result 저장, status 갱신
                                  ▼
                               [MySQL]
                                  ▲
[프론트(React)] ──GET /api/...──▶ [Spring]  조회 전용 (raw 무파싱)
```

| 컴포넌트 | 역할 |
| --- | --- |
| FastAPI | ingest 수신, DB 저장, LLM RCA 분석, 결과 저장 |
| Spring | 프론트용 조회 API. 분석·파싱 안 함 |
| 프론트 | Dashboard / Reports / ReportDetail(Summary·Cause·Impact·Action 탭) |

> ⚠️ **7/8 오전 회의 확정 필요 — 저장 주체**: 본 스펙은 **FastAPI가 DB에 직접 쓰는 구조(B안)** 기준. 그러나 팀 정본(기능정의서 SVC-01 "Spring이 리포트·raw 저장", D-001, 슬라이스 1.5, WBS I8 "에이전트↔Spring 연동")은 **FastAPI→Spring 저장 API(A안)** 를 전제한다.
> - **A안**: 스키마·엔티티 소유가 Spring 한 곳, 쓰기 경로 단일 — 대신 홉 추가 + Spring 쓰기 API 필요
> - **B안(현 스펙)**: 단순·insert 빠름 — 대신 두 앱이 DB 스키마 공유(DDL 소유권 정리 필요) + SVC-01·I8 문서 수정 필요
> B안 확정 시 I8은 "Spring 연동 API"가 아니라 "공유 스키마 정합 확인"으로 재정의.

`report.status` 흐름: `OPEN`(저장 직후) → `ANALYZING`(분석 중) → `DONE`(결과 저장) / `FAILED`(분석 실패)

## 1. 공통 데이터 형태

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

## 2. FastAPI 명세 (수집기 → FastAPI)

### POST /ingest

수집기가 정규화 번들을 전달. report 생성 + 3종 insert를 **한 트랜잭션**으로 수행 후,
LLM 분석을 **비동기로 트리거**하고 즉시 응답.

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

**에러**: `422` 스키마 위반(ts 형식·필수 필드), `409` 중복 `bundle_id`(기존 `report_id` 반환), `500` 저장 실패(트랜잭션 롤백, report 미생성)

> 분석 완료를 기다리지 않는다. 프론트는 status 폴링(Spring 조회)으로 DONE 확인.

## 3. Spring 명세 (프론트 → Spring)

베이스: `/api` · 인증 없음(로그인 스킵)

### GET /api/reports — 목록 (Reports 페이지)

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

### GET /api/reports/{id} — 상세 (ReportDetail)

목록 아이템 + `trigger` + `result`(§1 형태, 미완료면 `null`) + 3종 건수:

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

**에러 공통 형태** (Spring 전 엔드포인트): `{ "error": { "code": "REPORT_NOT_FOUND", "message": "..." } }`

### GET /api/dashboard — 대시보드 요약

```json
{
  "statusCounts": { "OPEN": 1, "ANALYZING": 2, "DONE": 39, "FAILED": 0 },
  "recentReports": [ /* 목록 아이템 5건 */ ]
}
```

> 화면 확정 후 필드 조정. 목록 재사용으로 충분하면 이 엔드포인트 삭제.

## 4. 후순위 (기본 파이프라인 완성 후)

| 엔드포인트 | 용도 | 비고 |
| --- | --- | --- |
| `GET /api/reports/{id}/logs·metrics·traces` | VizTab 시각화 데이터 | ts 범위·service 필터 쿼리 필요 |
| `GET /api/reports/{id}/agent-logs` | AgentLogTab | FastAPI가 분석 중 남기는 에이전트 로그 — 저장 테이블도 이때 추가 |
| `POST /reports/{id}/analyze` (FastAPI) | 재분석 | FAILED 재시도 필요해지면 |
| 로그인/인증 | — | 스킵 확정 |
