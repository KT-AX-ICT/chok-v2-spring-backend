# RCA 저장 스키마 설계서

> 상태: **확정** · DBMS: MySQL 8.0+ · DDL: [`schema.sql`](./schema.sql)
> DDL 소유: **Spring 단독** (A안, 7/7 확정) — FastAPI는 DB에 직접 접근하지 않는다.

## 1. 테이블 구조

`report`(부모) 1 : N `log` / `metric` / `trace`.
relation 컬럼은 부모가 아니라 **자식 3종에 `report_id` FK**로 존재한다.

3종 공통 골격 `{ report_id, ts, service, raw }`:

| 컬럼 | 타입 | 의미 |
|------|------|------|
| `report_id` | BIGINT FK | 소속 report (relation, `ON DELETE CASCADE`) |
| `ts` | DATETIME(3) | 정규화된 타임스탬프, **UTC 고정** — 로그 원본이 µs 정밀도라 ms(3)까지 보존 (같은 초 안 순서 복원용) |
| `service` | VARCHAR(128) | canonical service name |
| `raw` | JSON | 원본 그대로 (무파싱). 로그처럼 원본이 텍스트인 경우 `{"line": "..."}` 형태로 감싼다 |

`report` 부모에는 `bundle_id`(엣지 멱등 키, UNIQUE) · `trigger_info`(발화 신호 JSON) · `window_from/to` · `status` · `result`가 있다 — [`schema.sql`](./schema.sql) 참조.

**분량 상한**: 자식 3종은 원천 전량이 아니라 **±Δ·Top-K로 제한된 번들만** 저장한다(NFR-01·02, FR-S-01 "실제 사용 raw만"). report당 수백~수천 행이 정상 범위.

## 2. 유니크·인덱스 기준

**원칙: 실제 쿼리 경로에만 건다.** 조회 API가 전부 report 경유라 자식 테이블의 글로벌 인덱스는 투기적 — 빼고 시작한다.

| 대상 | 제약/인덱스 | 근거 쿼리 |
|---|---|---|
| `report.bundle_id` | **UNIQUE** (`uq_report_bundle`) | 엣지 재시도 멱등 — 중복 insert 시 DB가 막고 API는 409 + 기존 report_id 반환. 멱등의 최종 방어선은 애플리케이션 체크가 아니라 이 제약 |
| `report(status, created_at)` | INDEX | `GET /api/reports?status=` 필터 + 최신순 페이징, dashboard status 집계 |
| 자식 3종 `(report_id, ts)` | INDEX (복합) | 상세 counts, VizTab의 report 내 시간 범위 조회. leftmost가 FK 인덱스 겸용 — 별도 `(report_id)` 단독 인덱스 불필요 |
| 자식 3종 자연 유니크 | **의도적으로 없음** | 동일 로그 라인이 실제로 중복 존재 가능 — 중복 방지는 report 단위(bundle_id)에서 끝냄 |
| ~~`(ts, service)` 글로벌~~ | 제거 | 리포트 횡단 시간 조회가 현재 API에 없음. 횡단 분석(비교·이력, SVC-03)이 생기면 그때 추가 |

## 3. 3테이블 분리 근거

지금 3종 구조는 동일하나, 곧 각자 고유 컬럼이 붙을 것으로 보고 분리한다.
고유 컬럼은 **필요해지는 시점에 `ALTER TABLE ... ADD`** 로 추가 (지금 미리 만들지 않음):

- `metric` → 집계 쿼리 필요 시 `metric_name`, `value DOUBLE`
- `trace` → span 단위 조회 필요 시 `trace_id`, `span_id`, `parent_span_id`

## 4. 개념

핵심 원칙: **"수집기 정규화 스키마 = DB 저장 스키마"가 하나의 계약이다.**
전달 포맷과 저장 포맷을 분리하지 않는다. 그래서 어느 단에서 파싱하는지 혼란이 없다.

- **정규화(normalize)** ≠ **파싱(parse)**
- **전달·저장 계약**은 `{ ts, service, raw }` — 이 계약에서 수집기가 보장하는 건 `ts`·`service` 표준화까지고, 알맹이(`raw`)는 손대지 않는다.
- 단, 엣지 **내부**에는 별도의 정규화 스키마가 있다([`docs/normalization-schema.md`](../../docs/normalization-schema.md), Parquet) — baseline·트리거 계산에 metric `value`·span `duration_us` 등 파싱이 필수라서다. **엣지 내부용(트리거) ≠ 전달·저장 계약(본 문서)** — 회의(7/7)의 "얇은 vs 전체 정규화" 질문의 답: 전달·저장은 얇게, 트리거용 파싱은 엣지 내부에 가둔다.
- `raw` 파싱은 **저장까지 하지 않고**, RCA **분석 단계(FastAPI의 LLM 분석)에서만** 수행한다.

`raw`를 통째로 보관하는 이유: 로그/메트릭/트레이스는 소스마다 포맷이 제각각이라,
수집 시점에 파싱하면 새 포맷이 나올 때마다 스키마가 깨진다. 원본 보관 → 분석 시 필요한 것만 해석.

## 5. 데이터 흐름

```
[엣지 수집기]  raw 수집 → ts·service 정규화 → { ts, service, raw }
     │  (이 스키마가 곧 수집기 계약 = 전달 포맷 = 저장 포맷)
     ▼
[FastAPI]  번들 수신 → Spring 내부 저장 API 호출 (raw 무파싱)
     │      → LLM RCA 분석 (raw 파싱은 여기서만) → 결과를 Spring에 PATCH
     ▼
[Spring]  DB 단독 소유 — 저장(internal API) + 프론트 조회 API (raw 무파싱)
     ▼
[MySQL]   report(부모, result JSON) ─< log / metric / trace  (raw = 원본)
```

**저장 주체 = Spring (A안, 7/7 확정)**: FastAPI는 DB에 직접 접근하지 않는다. DDL·엔티티 소유는 Spring 한 곳 — 스키마 변경 시 마이그레이션 주체가 명확하고, WBS I8(에이전트↔Spring 연동)이 곧 internal API 2종이다.

| 질문 | 답 |
|------|-----|
| 수집기 schema로 정제 후 FastAPI 전달? | 네. 정제 = `ts`·`service` 정규화까지만. `raw`는 원본 |
| 3종 전부 파싱해서 저장? | 아니요. `raw`는 저장까지 무(無)파싱 |
| 어디서 파싱? | 저장 경로는 전부 무파싱. **FastAPI의 LLM 분석 시점에만** |
| 누가 DB에 쓰나? | **Spring만.** FastAPI는 `POST/PATCH /api/internal/reports*` 호출 |

## 6. Spring 엔티티 매핑

Hibernate 6+ 기준. `raw`는 `@JdbcTypeCode(SqlTypes.JSON)`으로 통째 매핑 (파싱 안 함):

```java
@Entity
@Table(name = "log")
public class Log {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    private LocalDateTime ts;
    private String service;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> raw;   // 분석 때만 꺼내 봄
}
```

`metric` / `trace` 동일 패턴. `Report`에는 필요 시 `@OneToMany(mappedBy = "report")` 역참조.

## 7. 정합성 테스트 계획 (Gate2 구현 시, 7/13~)

**장치부터**: `schema.sql`을 Flyway `V1__init.sql`로 채택 + `spring.jpa.hibernate.ddl-auto=validate`.
→ 엔티티↔DDL 정합이 **앱 기동마다 자동 검증**된다 (컬럼 타입·이름 어긋나면 기동 실패). 이게 정합성 테스트의 절반.

나머지 절반 — `@DataJpaTest`(Testcontainers MySQL 8) 한 클래스로:

| # | 테스트 | 검증 대상 |
|---|---|---|
| T1 | 같은 `bundle_id` 두 번 저장 → `DataIntegrityViolationException` | UNIQUE 제약 → API 409 매핑 |
| T2 | report 삭제 → 자식 3종 0건 | `ON DELETE CASCADE` |
| T3 | `ts` `.490` 저장 후 조회 → ms 보존 | `DATETIME(3)` ↔ `LocalDateTime` 왕복 |
| T4 | `raw` JSON 저장 후 조회 → 내용 동일 | `@JdbcTypeCode(JSON)` 왕복 (한글·중첩 포함) |
| T5 | `POST /api/internal/reports` → `GET /api/reports/{id}` 응답 일치 | 저장↔조회 계약 정합 (counts·trigger 포함) |
| T6 | `PATCH status=DONE+result` → 상세에 result 노출, `ANALYZING`이면 `result: null` | status 전이 + result 계약 |

H2 금지 — JSON 타입·DATETIME(3) 동작이 MySQL과 달라 정합성 테스트 목적을 잃는다.
