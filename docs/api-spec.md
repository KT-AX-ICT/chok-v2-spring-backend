# API 명세서

> 상태: **v0.2.1** · 관련: [`schema.md`](./schema.md) / [`schema.sql`](./schema.sql)
> 범위: 기본 파이프라인만. 로그인 스킵, VizTab·AgentLogTab은 후순위.
>
> **v0.2 (7/9)**: 프론트 명세와 대조 후 통일 — 대시보드 응답 프론트안 채택(`summary` 3종 KPI), 목록 필터 파라미터(`severity·from·to·search·sort`) 확정, 목록 아이템 재정의(`type·service·severity·summary·detectedAt` 추가), 상세 `{report, counts, detail}` 봉투 채택, result 4키 → **detail 5키 계약**(`rca·summary·evidence·impact·actions`). `report.severity` 컬럼 신설 전제(스키마 반영 필요).
> **v0.2.1 (7/9)**: 프론트 조회 API는 **`DONE`만 노출** — `status` 필드·쿼리 파라미터를 프론트 응답에서 제거(null 케이스 소멸). 내부 상태기계(§5)는 유지.

## 0. 아키텍처 / 역할 분담

```
[엣지 수집기] ──POST /ingest──▶ [FastAPI] ──POST /api/internal/reports──▶ [Spring] ──▶ [MySQL]
                                  │ LLM RCA 분석 (raw 파싱은 여기서만)        ▲
                                  └──PATCH /api/internal/reports/{id}────────┘  (status·severity·result)
[프론트(React)] ──GET /api/...──▶ [Spring]
```

| 컴포넌트 | 역할 |
| --- | --- |
| FastAPI | ingest 수신, Spring 저장 API 호출, LLM RCA 분석, 결과 PATCH |
| Spring | **DB 단독 소유** — 저장(internal API) + 프론트 조회 API. 분석·파싱 안 함 |
| 프론트 | Dashboard / Reports / ReportDetail(요약·원인·영향·조치 탭) |

> ✅ **저장 주체 = Spring 확정 (A안, 7/7)** — SVC-01·D-001·슬라이스 1.5와 정합. WBS I8(에이전트↔Spring 연동) = §5 internal API 2종. FastAPI는 DB에 직접 접근하지 않는다. Spring이 먼저 완성되면 FastAPI 쪽은 internal API를 mock으로 치다가 실연동(7/7 회의 I8 당김 합의).

`report.status` 흐름: `OPEN`(Spring, 저장 직후) → `ANALYZING` → `DONE` / `FAILED` — 전환은 전부 **FastAPI가 PATCH로 요청, Spring이 기록** · **내부 전용 — 프론트 조회 API(§4)에는 미노출, DONE만 조회 가능 (v0.2.1)**

## 1. 엔드포인트 한눈에

### 프론트 → Spring (베이스 `/api`, 인증 없음)

| Method | Path | 용도 | 화면 | 상태 | 상세 |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/reports` | 리포트 목록 (필터·검색·정렬·페이징) | Reports | ✅ 기본 | §4.1 |
| GET | `/api/reports/{id}` | 리포트 상세 (report+counts+detail) | ReportDetail | ✅ 기본 | §4.2 |
| GET | `/api/dashboard` | KPI 3종 + 최근 리포트 | Dashboard | ✅ 기본 | §4.3 |
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
| PATCH | `/api/internal/reports/{id}` | status 전환·severity·result 기록 | ✅ 기본 | §5.2 |

> internal 경로는 프론트 라우팅에서 제외만 하고 인증 없음 (내부 팀 도구, D-013).

## 2. 공통 데이터 형태

### ts 규약 (전 API 공통)

`yyyy-MM-dd HH:mm:ss.SSS` 문자열, **UTC 고정** (로그 원본 µs → ms 절사, DB `DATETIME(3)` 대응).
프론트 표시(`YYYY-MM-DD HH:mm`)·시간대 변환은 프론트 몫. 단 **날짜 필터·todayCount의 "오늘" 경계는 KST 기준으로 서버가 계산**(§4.1·§4.3).

### 3종 아이템 (수집기 계약과 동일)

```json
{ "ts": "2025-11-04 00:01:57.490", "service": "media-service", "raw": { } }
```

### 목록 아이템 (Reports 목록 · Dashboard recentReports 공용)

```json
{
  "id": 1,
  "title": "Svc_Kill_Media_20251104_000111",
  "type": "Svc_Kill",
  "service": "media-service",
  "severity": "HIGH",
  "summary": "media-service가 00:01:57에 종료되어 104초간 로그 침묵...",
  "detectedAt": "2025-11-04 00:01:57",
  "createdAt": "2026-07-07 10:00:00"
}
```

- `type`: 장애 유형 — `trigger.signal`에서 매핑 (signal enum 확정과 한 묶음, §6 쟁점 3)
- `service`: 대표 서비스 = `trigger.services[0]`
- `severity`: `HIGH`/`MID`/`LOW` **대문자** — 프론트가 소문자 매핑(합의됨). 원천은 LLM 분석 → `report.severity` 컬럼 (§5.2에서 기록)
- `detectedAt`: 감지 시각 = `trigger.ts` (프론트 `time` → 이름 통일)
- 목록·대시보드·상세는 **`DONE` 리포트만 노출**(v0.2.1) — `severity`·`summary` 항상 채워짐, 프론트 null 처리 불필요
- `windowFrom`/`windowTo`는 목록에서 제외 (상세 `report`에만 포함)

### detail (분석 결과 — `report.result` JSON 그대로, 탭과 1:1)

**최상위 5키 계약 고정**: `rca` · `summary` · `evidence` · `impact` · `actions`. 내부 세부 필드는 optional 허용 (LLM이 못 채우면 생략, 프론트는 생략 렌더).

```json
{
  "rca":     { "rootCause": "media-service 프로세스 강제 종료...", "propagation": "media-service → compose-post → nginx", "confidence": 94 },
  "summary": { "highlight": "media-service가 00:01:57에 종료되어 104초간 로그 침묵...", "chips": [], "errorTags": [], "neutralTags": [] },
  "evidence": {
    "log":    { "source": "...", "conclusion": "...", "lines": [ { "timestamp": "...", "level": "ERROR", "msg": "..." } ] },
    "trace":  { "source": "...", "conclusion": "...", "spans": [ { "traceId": "...", "from": "...", "to": "...", "duration": 0, "status": "..." } ] },
    "metric": { "source": "...", "conclusion": "...", "items": [ { "label": "...", "value": "...", "threshold": "...", "exceeded": true } ] }
  },
  "impact":  { "metrics": [ { "label": "...", "value": "..." } ], "affected": [ { "service": "compose-post-service", "errors": 12, "type": "..." } ] },
  "actions": { "steps": ["media-service 재시작 정책 점검", "..."], "recovery": "..." }
}
```

**키 ↔ 화면 매핑 · 필수 필드** (FastAPI/에이전트 산출 기준):

| 키 | 화면 구역 | 필수 (렌더 최소선) | optional (못 채우면 키 생략) |
|---|---|---|---|
| `rca` | 상세 상단 RCA 결론 블록 | `rootCause`, `propagation` (`"A → B → C"` 문자열) | `confidence` (숫자 %) |
| `summary` | 요약 탭 | `highlight` (요약 한 문단) | `chips[]`, `errorTags[]`, `neutralTags[]` |
| `evidence` | 원인 탭 (Log/Trace/Metric 서브탭) | `log`/`trace`/`metric` 각각의 `conclusion` — 모달별 소견 한 문장, 서브에이전트 3종 출력과 1:1 | `log.lines[]`, `trace.spans[]`, `metric.items[]`, 각 `source` |
| `impact` | 영향 탭 | `affected[]` (`[{service, ...}]`) | `metrics[]`, `affected[].errors`/`type` |
| `actions` | 조치 탭 | `steps[]` (문자열 배열) | `recovery` |

- **5개 키 이름·존재는 고정 계약** — 프론트가 키 이름으로 화면을 그리므로 키 하나가 빠지면 해당 탭이 통째로 빈 화면
- optional 필드는 **`null`을 넣지 말고 키 자체를 생략** — 프론트가 생략 렌더
- ⚠️ **`severity`는 result 안이 아니라 PATCH 바디 최상위** (§5.2) — DB 컬럼으로 저장돼 대시보드 집계·목록 필터에 쓰임
- 4키 → 5키 배경: 프론트 상세가 RCA 결론 블록(요지)과 원인 탭(근거)을 분리 렌더 → 구 `cause`를 `rca`(결론) + `evidence`(근거)로 분리, `action` → `actions` 개명, `impact` 구조 프론트안 채택
- Spring은 JSON 패스스루(저장·반환만) — 이 구조를 만드는 책임은 FastAPI(LLM 프롬프트) 쪽. **산출 가능 여부는 에이전트팀 확인 필요 (§6 쟁점 2)**, 구조화 근거(`lines`·`spans`·`items`)가 무리면 `conclusion`만 채우는 것으로 시작

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
- `trigger`: 발화 신호 컨텍스트 — RCA의 핵심 입력. 목록 `type`·`service`·`detectedAt`의 원천이기도 함 (§2). `signal` 값 체계는 SDK 스키마(유경·예지)와 함께 확정
- 3종 배열은 **±Δ·Top-K로 제한된 번들** — 원천 전량 금지 (NFR-01·02)
- ⚠️ **LLM 입력 정제 (유스케이스·기능정의서 v0.2 원칙, D-020)**: `title`·`bundle_id`(시나리오 명칭 포함)·원본 파일명은 **저장·화면 표시용** — FastAPI가 LLM에 번들을 넘길 때는 **제외** (정답 라벨 유출 방지, Gate3 채점 무결성. 트리거=이상 감지 / LLM=원인 판단 분리)

**Response** `201`

```json
{ "report_id": 1 }
```

**에러**: `422` 스키마 위반(ts 형식·필수 필드), `409` 중복 `bundle_id`(Spring 응답 그대로 전달, 기존 `report_id` 포함), `502` Spring 저장 실패(report 미생성 — 엣지가 재시도)

> 분석 완료를 기다리지 않는다. 프론트 조회 API는 DONE만 노출하므로(§4) 분석이 끝나면 목록 폴링에 자연히 나타난다.

## 4. Spring 상세 (프론트 → Spring)

**에러 공통 형태** (전 엔드포인트): `{ "error": { "code": "REPORT_NOT_FOUND", "message": "..." } }`

### 4.1 GET /api/reports — 목록 (Reports 페이지)

**Query** (전부 선택, 프론트 임시 네이밍과 통일 완료) — **응답은 `DONE` 리포트만** (v0.2.1):

| 파라미터 | 형식 | 설명 |
| --- | --- | --- |
| `page` / `size` | 0-base / 기본 20 | 페이지네이션 (기확정) |
| `severity` | `HIGH`/`MID`/`LOW` | 심각도 필터 |
| `from` / `to` | `yyyy-MM-dd` | 생성일 범위, **KST 날짜 기준** (서버가 KST 경계로 변환) |
| `search` | 문자열 | `title`·`summary` LIKE 검색 |
| `sort` | `필드,방향` (Pageable 포맷) | 예 `sort=createdAt,desc` (기본값). 허용 필드 화이트리스트: `createdAt`·`severity`·`detectedAt` |

**Response** — Spring Page 포맷 (프론트가 `content`→`items`, `totalElements`→`total` 매핑, 합의됨):

```json
{
  "content": [ /* 목록 아이템 (§2) */ ],
  "totalElements": 42, "totalPages": 3, "page": 0
}
```

### 4.2 GET /api/reports/{id} — 상세 (ReportDetail)

단일 호출로 헤더+본문 (프론트 `fetchReportView` 대응, 봉투 구조 프론트안 채택):

```json
{
  "report": {
    "id": 1, "title": "...", "type": "Svc_Kill", "service": "media-service",
    "severity": "HIGH", "summary": "...",
    "detectedAt": "2025-11-04 00:01:57", "createdAt": "2026-07-07 10:00:00",
    "windowFrom": "2025-11-04 00:00:00", "windowTo": "2025-11-04 00:10:00",
    "trigger": { "ts": "2025-11-04 00:01:57.490", "signal": "log_silence+start_log_x2", "services": ["media-service"] }
  },
  "counts": { "logs": 842, "metrics": 240, "traces": 120 },
  "detail": { /* §2 detail 5키 */ }
}
```

- `report` = 목록 아이템 + `windowFrom/windowTo` + `trigger`
- `detail` = `report.result` JSON 그대로 — DONE만 노출되므로 항상 존재
- `404` 없는 id **또는 미완료(DONE 아님) id** — 에러 공통 형태(`REPORT_NOT_FOUND`). 프론트는 NotFound 연결만
- counts는 번들이 ±Δ·Top-K 제한이므로 수백~수천 수준이 정상 — 수십만이면 엣지 정제가 빠진 것

### 4.3 GET /api/dashboard — 대시보드 요약 (프론트안 채택)

```json
{
  "summary": { "total": 42, "highCount": 3, "todayCount": 5 },
  "recentReports": [ /* 목록 아이템(§2) 5건, createdAt desc */ ]
}
```

- `total`: `DONE` 리포트 수
- `highCount`: `severity = HIGH` 수
- `todayCount`: **KST 오늘** 생성분 수 (DONE 기준, 서버가 KST 경계 계산)
- ~~`statusCounts`~~ 폐기 — 프론트 KPI 카드 3종과 1:1로 교체 (v0.2)

## 5. Spring 내부 API 상세 (FastAPI → Spring) — WBS I8

### 5.1 POST /api/internal/reports — 번들 저장

요청 바디 = **`/ingest`(§3.1)와 동일 계약** (bundle_id·title·window·trigger·3종). report 생성 + 3종 insert를 **한 트랜잭션**으로.
`type`·`service`·`detectedAt`은 Spring이 `trigger`에서 파생 저장 (별도 필드 아님).

**Response** `201` `{ "report_id": 1 }`

**에러**: `409` 중복 `bundle_id` — `{ "error": { "code": "DUPLICATE_BUNDLE", "message": "...", "reportId": 1 } }` (DB UNIQUE 제약이 최종 방어선), `422` 필수 필드·ts 형식 위반, `500` 트랜잭션 롤백(report 미생성)

### 5.2 PATCH /api/internal/reports/{id} — 상태 전환·결과 기록

```json
{ "status": "ANALYZING" }
{ "status": "DONE", "severity": "HIGH", "result": { "rca": {}, "summary": {}, "evidence": {}, "impact": {}, "actions": {} } }
{ "status": "FAILED" }
```

**Response** `200` (갱신된 상세). **에러**: `404` 없는 id, `422` `DONE`인데 `severity`/`result` 누락 · 허용되지 않는 status/severity 값

- **v0.2 변경**: `DONE` 시 `severity` 필수 (LLM 판정 → `report.severity` 컬럼 기록). `result`는 5키 계약(§2)
- 상태 전이 검증은 최소만: `DONE` ⇒ `severity`+`result` 필수. 역방향 전이 차단 같은 상태기계는 필요해지면 추가

## 6. 회의 쟁점 (우선순위순)

1. **`report.severity` 컬럼 신설** — `HIGH/MID/LOW`, NULL 허용(분석 전). 원천은 LLM 판정, FastAPI가 `PATCH DONE`에 포함(§5.2). 대시보드 `highCount`·목록 severity 필터가 여기 걸림. **스키마 변경 + internal API 계약 변경 → 가희와 확인**
2. **detail 5키 계약(`rca·summary·evidence·impact·actions`) 산출 가능 여부** — 에이전트팀(예지·가희) 확인. 기존 "result 4키" 안건을 이것으로 대체. `confidence`·`trace.spans`·`metric.items` 등은 optional로 못 박음(§2)
3. **`type` 값 체계** — `trigger.signal` enum 확정과 한 묶음 (유경, SDK 트리거 구현). 예: `Svc_Kill`·`Code_Stop`·`Perf_CPU` ↔ signal 매핑표
4. **프론트 목록 `status` 컬럼(hitl/auto/resolved) 제거 확인** — 백엔드는 status 미노출 확정(v0.2.1)이라 데이터 원천 없음. 프론트 화면에서 컬럼 제거 필요 (담당자 수동 처리 기능은 MVP 제외)
5. **KST 기준 확정** — `todayCount`·`from/to` 날짜 경계는 KST, 서버 계산(§4.1·§4.3). ts 저장·전송은 UTC 유지
6. **ts 규약** `yyyy-MM-dd HH:mm:ss.SSS` UTC — SDK↔FastAPI 계약(유경·예지)과 동일한지 확인 (기존 안건 유지)

### 프론트와 합의 완료 (v0.2 반영분)

- [x] 필드 네이밍 camelCase / 목록 서버 사이드 페이징(Page 포맷) / 상세 단일 호출 — 기합의
- [x] 대시보드 응답 = 프론트안 `summary{total,highCount,todayCount}` + `recentReports` (§4.3)
- [x] 목록 필터 파라미터명 `severity·from·to·search·sort` + 아이템 구조 (§2·§4.1) — 프론트 `time` → `detectedAt`만 개명
- [x] 상세 봉투 `{report, counts, detail}` (§4.2)
- [x] severity 값: API 대문자 `HIGH/MID/LOW`, 프론트 소문자 매핑
- [x] 404: 기존 계약 유지, 프론트 NotFound 연결만 (백엔드 변경 없음)
- [x] 프론트 조회 API는 `DONE`만 노출 — `status` 필드·쿼리 파라미터 제거, 분석 중/실패 리포트는 화면 미표시 (v0.2.1 백엔드 확정 — 프론트 공유 필요)
