# API 명세서

> 상태: **v0.2.1 + 7/10 개정** (버전 라벨 확정은 명일 회의 — 노션 기준) · 관련: [`schema.md`](./schema.md) / [`schema.sql`](./schema.sql)
> 범위: 기본 파이프라인만. 로그인 스킵, VizTab·AgentLogTab은 후순위.
>
> **v0.2 (7/9)**: 프론트 명세와 대조 후 통일 — 대시보드 응답 프론트안 채택(`summary` 3종 KPI), 목록 필터 파라미터(`severity·from·to·search·sort`) 확정, 목록 아이템 재정의(`type·service·severity·summary·detectedAt` 추가), 상세 `{report, counts, detail}` 봉투 채택, result 4키 → **detail 5키 계약**(`rca·summary·evidence·impact·actions`). `report.severity` 컬럼 신설 전제(스키마 반영 필요).
> **v0.2.1 (7/9)**: 프론트 조회 API는 **`DONE`만 노출** — `status` 필드·쿼리 파라미터를 프론트 응답에서 제거(null 케이스 소멸). 내부 상태기계(§5)는 유지.
> **7/10① (D-021)**: `trigger` 2키 축소 — `ts`→**`timestamp`** 개명, **`signal` 제거**, `services`→**`modality`** 개명(값은 서비스명이 아닌 감지 모달리티 `log`/`metric`/`trace`) — D-020 정제 원칙의 계약 확장. 3종 `raw` JSON→**string**.
> **7/10② (D-022, 노션 대조 확정)**: `ts`→`timestamp` 개명을 **전 계약으로 확대**(3종 아이템 포함 — DB 컬럼 `ts`는 유지) · `window` `from/to`→**`start/end`**(상세 `windowStart/End`) · 목록 **`type` 원천 = LLM 판정**(Q-007 일부 해소, `service`는 미결) · 후순위 재분석 `POST /ingest/{id}` + 상태 확인 `GET /ingest/{id}` · **결과 PATCH 전부 DONE 처리**(severity는 컬럼·계약만 신설, 필수 검증 완화). 목록 `status` 쿼리 파라미터는 **불채택**(DONE만 노출 유지).
> **7/14 (D-023)**: `/ingest` 계약 개편 — ① **`bundle_id`·`title`·`modality_info` 전부 제거** — 페이로드에서 라벨성 필드 걷어냄(D-020 정제 목표 계약 수준 달성) ② **멱등성 = 파생 단일키** — `bundle_id` 대신 `trigger_info.trigger_time`으로 판별, `trigger_time` 실컬럼 승격 + `UNIQUE(trigger_time)`(§5.1·schema.sql), 409 `DUPLICATE_TRIGGER` (재분석은 id기반 UPDATE라 새 INSERT 없어 단일키로 충분) ③ `trigger`→**`trigger_info`**(`timestamp`→`trigger_time`, `modality`→`triggered_by`) — DB 컬럼명과 정합 ④ **`bundle_version`**(계약 버전) 추가 ⑤ 201 응답 **`report_id`→`ingest_id`**(비동기 추적 토큰 — ack 시점 report 미생성) ⑥ **내부 API POST 1개로 통합** — 분석 완료 *후* 저장, POST 바디에 `status·severity·result` 일괄, PATCH 후순위 폐기(§5) ⑦ **schema.sql: `bundle_id`·`title` 컬럼·`uq_report_bundle` DROP, `trigger_time` 컬럼(NOT NULL)+`UNIQUE(trigger_time)` 신설.**

## 0. 아키텍처 / 역할 분담

```
[엣지 수집기] ──POST /ingest──▶ [FastAPI] ──(LLM RCA 분석)──▶ POST /api/internal/reports──▶ [Spring] ──▶ [MySQL]
                                  raw 파싱·분석은 여기서만 · 분석 완료 후 status·severity·result 일괄 저장(1 트랜잭션)
[프론트(React)] ──GET /api/...──▶ [Spring]
```

| 컴포넌트 | 역할 |
| --- | --- |
| FastAPI | ingest 수신(ack), LLM RCA 분석, **분석 완료 후** Spring 저장 API 1회 호출(번들+결과 일괄) |
| Spring | **DB 단독 소유** — 저장(internal API) + 프론트 조회 API. 분석·파싱 안 함 |
| 프론트 | Dashboard / Reports / ReportDetail(요약·원인·영향·조치 탭) |

> ✅ **저장 주체 = Spring 확정 (A안, 7/7)** — SVC-01·D-001·슬라이스 1.5와 정합. WBS I8(에이전트↔Spring 연동) = §5 internal API 2종. FastAPI는 DB에 직접 접근하지 않는다. Spring이 먼저 완성되면 FastAPI 쪽은 internal API를 mock으로 치다가 실연동(7/7 회의 I8 당김 합의).

`report.status` 흐름 **(7/14 D-023 단순화)**: 저장이 분석 완료 후에 일어나므로 report는 **생성 시점에 바로 `DONE`** (POST 바디에 `status` 포함, §5.1) — 분석 전엔 report가 없으므로 `OPEN`·`ANALYZING` 상태는 MVP에 존재하지 않는다. `status`는 **내부 전용 — 프론트 조회 API(§4)에는 미노출, DONE만 조회 가능 (v0.2.1)**.
상태기계(`OPEN`→`ANALYZING`→`DONE`/`FAILED`)와 PATCH 기반 전환은 **후순위 확장**(§5.2) — 실패(`FAILED`) 노출·진행 표시가 필요해지면 도입.

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
| POST | `/ingest/{id}` | 재분석 (FAILED 재시도) | ⏸ 후순위 | — |
| GET | `/ingest/{id}` | 분석 상태 확인 | ⏸ 후순위 | — |

### FastAPI → Spring (내부, 베이스 `/api/internal`) — WBS I8

| Method | Path | 용도 | 상태 | 상세 |
| --- | --- | --- | --- | --- |
| POST | `/api/internal/reports` | **분석 완료** 번들+결과 저장 (report+3종+`status·severity·result`, 1 트랜잭션) | ✅ 기본 | §5.1 |
| ~~PATCH~~ | ~~`/api/internal/reports/{id}`~~ | POST에 통합(D-023) — 상태기계 전환은 후순위 | ⏸ 후순위 | §5.2 |

> internal 경로는 프론트 라우팅에서 제외만 하고 인증 없음 (내부 팀 도구, D-013).

## 2. 공통 데이터 형태

### timestamp 규약 (전 API 공통)

필드명 **`timestamp`** (7/10 `ts`→개명, D-022 — DB 컬럼 `ts`는 유지, 매핑은 Spring 몫). `yyyy-MM-dd HH:mm:ss.SSS` 문자열, **UTC 고정** (로그 원본 µs → ms 절사, DB `DATETIME(3)` 대응).
프론트 표시(`YYYY-MM-DD HH:mm`)·시간대 변환은 프론트 몫. 단 **날짜 필터·todayCount의 "오늘" 경계는 KST 기준으로 서버가 계산**(§4.1·§4.3).

### 3종 아이템 (수집기 계약과 동일)

```json
{ "timestamp": "2025-11-04 00:01:57.490", "service": "media-service", "raw": "..." }
```

`service`는 string — 직접 넣을 수 없을 때(서비스가 죽어 기록이 없을 때)는 **빈 string**으로 전달 (7/14 노션 반영).
`raw`는 **string** (D-021) — 로그는 원본 한 줄 그대로, 메트릭·트레이스는 직렬화된 원본 문자열. 행당 원본 한 줄 대응이고 RCA 분석이 DB를 재조회하지 않으므로(FastAPI가 수신 번들로 분석) JSON 타입의 실익이 없다.

### 목록 아이템 (Reports 목록 · Dashboard recentReports 공용)

```json
{
  "id": 1,
  "type": "Svc_Kill",
  "service": "media-service",
  "severity": "HIGH",
  "summary": "media-service가 00:01:57에 종료되어 104초간 로그 침묵...",
  "detectedAt": "2025-11-04 00:01:57",
  "createdAt": "2026-07-07 10:00:00"
}
```

- **`title` 제거 (7/14 D-023)**: `/ingest`에서 빠짐 — 별도 title 개념 폐기(`trigger`→`trigger_info` 개명과 무관). 목록·상세·검색은 `type`+`service`+`detectedAt`+`summary`로 표시. DB `title` 컬럼도 제거(schema.sql)
- `type`: 장애 유형 — **LLM 판정** (7/10 확정, D-022). 전달·저장 경로(POST 바디 위치·컬럼화)는 §6 쟁점 3
- `service`: 대표 서비스 — ⚠️ **원천 재확정 필요**: D-021로 `trigger`가 서비스명을 담지 않음 (§6 쟁점 3)
- `severity`: `HIGH`/`MID`/`LOW` **대문자** — 프론트가 소문자 매핑(합의됨). 원천은 LLM 분석 → `report.severity` 컬럼 (§5.1에서 기록)
- `detectedAt`: 감지 시각 = `trigger_info.trigger_time` (프론트 `time` → 이름 통일)
- 목록·대시보드·상세는 **`DONE` 리포트만 노출**(v0.2.1) — `summary` 항상 채워짐. `severity`는 D-022 단순화 동안 null 가능(컬럼·계약만 신설, 채움 보장은 LLM 판정 연동 후). 분석 중(OPEN/ANALYZING) 행 노출은 **확장 범위**(MVP 제외, MVP-정의서 §11) — 도입 시 null 계약 신설 필요
- `windowStart`/`windowEnd`는 목록에서 제외 (상세 `report`에만 포함)

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
- ⚠️ **`severity`는 result 안이 아니라 POST 바디 최상위** (§5.1) — DB 컬럼으로 저장돼 대시보드 집계·목록 필터에 쓰임
- 4키 → 5키 배경: 프론트 상세가 RCA 결론 블록(요지)과 원인 탭(근거)을 분리 렌더 → 구 `cause`를 `rca`(결론) + `evidence`(근거)로 분리, `action` → `actions` 개명, `impact` 구조 프론트안 채택
- Spring은 JSON 패스스루(저장·반환만) — 이 구조를 만드는 책임은 FastAPI(LLM 프롬프트) 쪽. **산출 가능 여부는 에이전트팀 확인 필요 (§6 쟁점 2)**, 구조화 근거(`lines`·`spans`·`items`)가 무리면 `conclusion`만 채우는 것으로 시작

## 3. FastAPI 상세 (수집기 → FastAPI)

### 3.1 POST /ingest

수집기가 정규화 번들을 전달. FastAPI는 **즉시 ack**(`ingest_id`)하고 **LLM 분석을 비동기로 수행**한 뒤,
완료되면 번들+결과를 `POST /api/internal/reports`(§5.1)로 저장. (저장 트랜잭션은 Spring 몫 — 분석 전엔 report 미생성)

**Request**

```json
{
  "bundle_version": "1.0",
  "window": { "start": "2026-01-15T10:00:00Z", "end": "2026-01-15T10:03:00Z" },
  "trigger_info": {
    "trigger_time": "2026-01-15T10:01:30Z",
    "triggered_by": ["metric", "log"]
  },
  "logs":    [ { "timestamp": "...", "service": "...", "raw": "..." } ],
  "metrics": [ { "timestamp": "...", "service": "...", "raw": "..." } ],
  "traces":  [ { "timestamp": "...", "service": "...", "raw": "..." } ]
}
```

- `bundle_version` (7/14 D-023): 번들 계약 버전 — 계약 진화 시 수신 측 분기용. 현재 `"1.0"`
- **멱등성 (7/14 D-023, `bundle_id` 제거)**: 페이로드에 별도 키 없음 — 서버가 **`trigger_info.trigger_time`**으로 판별. 재전송해도 트리거 시각이 동일 → 중복 report 방지(`report.trigger_time` UNIQUE, §5.1). 재분석은 id기반 UPDATE(§1 후순위)라 새 INSERT가 없어 단일키로 충분. FastAPI는 ingest 시점에 같은 키로 사전 dedup 시 재분석 낭비도 방지. 재시도 근거: MVP-정의서 §7.3
- `trigger_info` (D-021→D-023 개명): **감지 시각(`trigger_time`)과 트리거가 걸린 모달리티(`triggered_by[]`, 값: `log`/`metric`/`trace`, 복수 가능)만 전달** — signal 상세·서비스명은 계약에서 제외. 트리거=이상 감지 / LLM=원인 판단 분리(D-020)의 계약 확장 — 감지 신호·의심 서비스를 LLM에 넘기면 정답 유출 소지. 목록 `detectedAt`의 원천 (§2)
- 3종 배열은 **±Δ·Top-K로 제한된 번들** — 원천 전량 금지 (NFR-01·02)
- ✅ **LLM 입력 정제 (D-020) — 계약 수준에서 해소**: 라벨 유출 필드(`title`·`bundle_id`·원본 파일명/`modality_info`)를 7/14 D-023으로 **페이로드에서 전부 제거**. `trigger_info`도 D-021로 서비스명 없이 모달리티만 남김 → **FastAPI가 LLM에 넘길 때 별도 마스킹 대상이 사라짐**(정답 라벨 유출 방지, Gate3 채점 무결성)

**Response** `201`

```json
{ "ingest_id": "..." }
```

- `ingest_id` (7/14 D-023): 이 ingest 요청의 **비동기 추적 토큰** — `report_id`가 아님(ack 시점엔 분석 전이라 report 미생성). 재분석/상태 확인(`/ingest/{id}`, 후순위)의 키.

**에러**: `422` 스키마 위반(timestamp 형식·필수 필드), `409` **중복 트리거**(같은 `trigger_time` 재전송 — `DUPLICATE_TRIGGER`, 기존 `report_id` 포함), `502` Spring 저장 실패(report 미생성 — 엣지가 재시도)

> `/ingest`는 **분석 완료를 기다리지 않고 즉시 ack**(`ingest_id`)한다. FastAPI가 분석을 마친 뒤 §5.1 POST로 report를 생성하며, 프론트 조회 API는 DONE만 노출하므로(§4) 목록 폴링에 자연히 나타난다.

## 4. Spring 상세 (프론트 → Spring)

**에러 공통 형태** (전 엔드포인트): `{ "error": { "code": "REPORT_NOT_FOUND", "message": "..." } }`

### 4.1 GET /api/reports — 목록 (Reports 페이지)

**Query** (전부 선택, 프론트 임시 네이밍과 통일 완료) — **응답은 `DONE` 리포트만** (v0.2.1):

| 파라미터 | 형식 | 설명 |
| --- | --- | --- |
| `page` / `size` | 0-base / 기본 20 | 페이지네이션 (기확정) |
| `severity` | `HIGH`/`MID`/`LOW` | 심각도 필터 |
| `from` / `to` | `yyyy-MM-dd` | 생성일 범위, **KST 날짜 기준** (서버가 KST 경계로 변환) |
| `search` | 문자열 | `summary` LIKE 검색 (7/14 D-023: `title` 제거로 `summary` 단독) |
| `sort` | `필드,방향` (Pageable 포맷) | 예 `sort=createdAt,desc` (기본값). 허용 필드 화이트리스트: `createdAt`·`severity`·`detectedAt` |

> `status` 쿼리 파라미터는 **불채택** (7/10, D-022) — DONE만 노출 원칙 유지. 분석 중 행 노출은 확장 범위(MVP-정의서 §11).

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
    "id": 1, "type": "Svc_Kill", "service": "media-service",
    "severity": "HIGH", "summary": "...",
    "detectedAt": "2025-11-04 00:01:57", "createdAt": "2026-07-07 10:00:00",
    "windowStart": "2025-11-04 00:00:00", "windowEnd": "2025-11-04 00:10:00",
    "trigger_info": { "trigger_time": "2025-11-04 00:01:57.490", "triggered_by": ["log"] }
  },
  "counts": { "logs": 842, "metrics": 240, "traces": 120 },
  "detail": { /* §2 detail 5키 */ }
}
```

- `report` = 목록 아이템 + `windowStart/windowEnd` + `trigger_info`
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

### 5.1 POST /api/internal/reports — 분석 완료 번들+결과 저장

**7/14 D-023: FastAPI가 분석을 마친 뒤 1회 호출.** 요청 바디 = `/ingest` 수신 계약(§3.1: `bundle_version`·`window`·`trigger_info`·3종) **+ 분석 결과** `status`·`severity`·`result`. report 생성 + 3종 insert를 **한 트랜잭션**으로. (구 PATCH가 하던 결과 기록을 이 POST가 흡수 — §5.2)

```json
{
  "bundle_version": "1.0",
  "window": { "start": "...", "end": "..." },
  "trigger_info": { "trigger_time": "...", "triggered_by": ["log"] },
  "logs": [], "metrics": [], "traces": [],

  "status": "DONE",
  "severity": "HIGH",
  "result": { "rca": {}, "summary": {}, "evidence": {}, "impact": {}, "actions": {} }
}
```

- **멱등성**: 별도 키 없음 — Spring이 `trigger_info.trigger_time`으로 판별. `trigger_time`을 실컬럼으로 승격해 **`UNIQUE (trigger_time)`**(§3.1·schema.sql). 재전송 시 409
- `status`는 MVP에서 항상 `DONE`(분석 완료분만 저장) — report는 `DONE`으로 바로 생성. `severity`는 result 밖 최상위(컬럼 저장), D-022 완화로 누락 시 NULL 허용
- `detectedAt`은 Spring이 `trigger_info.trigger_time`(=승격 컬럼)에서 파생. `type`은 LLM 판정(D-022), `service` 원천은 §6 쟁점 3

**Response** `201` `{ "report_id": 1 }` (내부 id — 프론트엔 `/ingest`의 `ingest_id`만 노출)

**에러**: `409` 중복 트리거(같은 `trigger_time`) — `{ "error": { "code": "DUPLICATE_TRIGGER", "message": "...", "reportId": 1 } }` (DB `UNIQUE(trigger_time)`가 최종 방어선), `422` 필수 필드·timestamp 형식·`DONE`인데 `result` 누락, `500` 트랜잭션 롤백(report 미생성)

### 5.2 ~~PATCH /api/internal/reports/{id}~~ — 상태 전환 (⏸ 후순위)

**7/14 D-023: MVP에서 미사용.** 결과 기록은 §5.1 POST가 흡수(분석 완료 후 저장이라 별도 전환 불필요). 아래는 상태기계(`ANALYZING`/`FAILED` 노출·진행 표시)가 필요해질 때의 **후순위 계약** 보존용:

```json
{ "status": "ANALYZING" }
{ "status": "DONE", "severity": "HIGH", "result": { "rca": {}, "summary": {}, "evidence": {}, "impact": {}, "actions": {} } }
{ "status": "FAILED" }
```

**Response** `200` (갱신된 상세). **에러**: `404` 없는 id, `422` `DONE`인데 `result` 누락 · 허용되지 않는 status/severity 값

- 도입 시 흐름: §5.1 POST가 `OPEN`으로 report만 먼저 생성 → 분석 진행 중 `ANALYZING` → 완료 `DONE`+result / 실패 `FAILED`. 현재는 이 분리를 하지 않고 §5.1 POST 한 번으로 `DONE` 생성
- 상태 전이 검증은 최소만: `DONE` ⇒ `result` 필수. 역방향 전이 차단 같은 상태기계는 필요해지면 추가

## 6. 회의 쟁점 (우선순위순)

1. **`report.severity` 컬럼 신설** — `HIGH/MID/LOW`, NULL 허용(분석 전). 원천은 LLM 판정, FastAPI가 `POST /api/internal/reports` 바디 최상위에 포함(§5.1, 구 `PATCH DONE`에서 D-023으로 이관). 대시보드 `highCount`·목록 severity 필터가 여기 걸림. **스키마 변경 + internal API 계약 변경 → 가희와 확인** · 7/10: 컬럼·계약만 신설하고 필수 검증은 완화, LLM 판정 연동은 후속 (D-022)
2. **detail 5키 계약(`rca·summary·evidence·impact·actions`) 산출 가능 여부** — 에이전트팀(예지·가희) 확인. 기존 "result 4키" 안건을 이것으로 대체. `confidence`·`trace.spans`·`metric.items` 등은 optional로 못 박음(§2)
3. **목록 `service` 원천 + `type` 전달 경로 (Q-007 잔여)** — `type`은 **LLM 판정으로 확정**(7/10, D-022). 남은 것: ① `type`의 전달·저장 경로 — severity처럼 `POST` 바디 최상위 vs `result` 내부, 컬럼화 여부 ② `type` 값 체계(`Svc_Kill`·`Code_Stop`·`Perf_CPU` 등) ③ `service`의 새 원천 — D-021로 trigger에서 서비스명 제거, 파생 불가 (유경·에이전트팀). `title`은 D-023으로 **제거 확정**(더 이상 안건 아님)
4. **프론트 목록 `status` 컬럼(hitl/auto/resolved) 제거 확인** — 백엔드는 status 미노출 확정(v0.2.1)이라 데이터 원천 없음. 프론트 화면에서 컬럼 제거 필요 (담당자 수동 처리 기능은 MVP 제외)
5. **KST 기준 확정** — `todayCount`·`from/to` 날짜 경계는 KST, 서버 계산(§4.1·§4.3). ts 저장·전송은 UTC 유지
6. **timestamp 규약** `yyyy-MM-dd HH:mm:ss.SSS` UTC — SDK↔FastAPI 계약(유경·예지)과 동일한지 확인 (기존 안건 유지). 7/10 필드명 `ts`→`timestamp` 전면 개명(D-022) — SDK 쪽 필드명도 동일한지 확인 필요. `/ingest`는 ISO8601 `Z` 표기 예시(§3.1)와 이 규약(공백 구분)이 달라 **표기 형식 통일도 안건**

### 프론트와 합의 완료 (v0.2 반영분)

- [x] 필드 네이밍 camelCase / 목록 서버 사이드 페이징(Page 포맷) / 상세 단일 호출 — 기합의
- [x] 대시보드 응답 = 프론트안 `summary{total,highCount,todayCount}` + `recentReports` (§4.3)
- [x] 목록 필터 파라미터명 `severity·from·to·search·sort` + 아이템 구조 (§2·§4.1) — 프론트 `time` → `detectedAt`만 개명
- [x] 상세 봉투 `{report, counts, detail}` (§4.2)
- [x] severity 값: API 대문자 `HIGH/MID/LOW`, 프론트 소문자 매핑
- [x] 404: 기존 계약 유지, 프론트 NotFound 연결만 (백엔드 변경 없음)
- [x] 프론트 조회 API는 `DONE`만 노출 — `status` 필드·쿼리 파라미터 제거, 분석 중/실패 리포트는 화면 미표시 (v0.2.1 백엔드 확정 — 프론트 공유 필요)
