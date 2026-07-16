# WORKLOG

> 작업 기록. 최신이 위로. 형식: `- HH:MM 내용`
> **아카이브 규칙**: 당일 기준 이틀 이상 지난 날짜 섹션은 [`WORKLOG-ARCHIVE.md`](./WORKLOG-ARCHIVE.md)로 이동 (worklog 갱신 시마다 검사).

## 2026-07-16 (목)

- 11:35 **코드리뷰 지적 6건 검증·대응 (SVC-01/02)** — 외부 리뷰 6건을 스키마/코드로 검증(맹종 아님): 전부 유효하나 reviewer의 dedup "길이초과" 예시는 try/catch 범위(report 저장) 밖·`service` null은 이미 처리됨을 확인. 수정 5건 → ① 저장 body `null` 500→422 ② signal 배열 null항목·`raw` 누락(NOT NULL) 저장 전 422 ③ dedup catch를 trigger_time 재조회 확인 시에만 409, 아니면 원 예외 재전파(오분류 방지) ④ 검색어 LIKE 메타문자 escape ⑤ todayCount 자정 경계(now 2회 읽기→단일 `todayRangeUtc`). #6(테스트)는 순수 유닛만 채택 → Timestamps/KstDates/ReportSpecs 3종(무-DB, 무-의존성 추가). 실배포 회귀 5/5 + 유닛 통과. 커밋 `85b4350`(fix)·`bb7669f`(test)
- 11:19 **조회 API 버그픽스 2건 (SVC-02 후속) + Docker E2E 재검증** — 어제 조회 API 리뷰 중 발견한 2건 처리. ① 조회 파라미터 파싱 실패가 **500** 나던 것 → `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException`(`{id}` 타입)·`DateTimeParseException`(`from`/`to` 날짜) 추가해 저장 경로와 동일한 **422 `INVALID_PAYLOAD`** 봉투로 통일(해피패스만 검증돼 사각지대였던 실패경로). ② 날짜 필터·todayCount 축을 `createdAt`(저장 시각)→`triggerTime`(=detectedAt, 장애 발생 시각)으로 정정 — 정렬 화이트리스트(detectedAt 제공)와 필터축 불일치 해소. 실배포 8080 검증: `?from=oops`·`/reports/abc` 422, seed(trigger_time 7/10·created_at 7/16)로 `from/to=7/10` 1건·`7/16` 0건 = 축 detectedAt 확정, 앱 clean 부팅으로 파생 쿼리 메서드명 검증. 커밋 `e29f55b`

## 2026-07-15 (화)

- 11:45 **Docker 배포 E2E 검증 — 슬라이스 1.5(Spring) 완료** — 앱 이미지 재빌드 후 8080 실배포에서 저장(POST)→목록/상세/대시보드 전 흐름 통과, named volume 데이터 유지 확인. compose 하드닝(127.0.0.1 바인딩·non-root)도 실배포 반영. (검증 — 코드 커밋 없음)
- 11:30 **프론트 리포트 조회 API 3종 (SVC-02, 슬라이스 1.5)** — GET `/api/reports`(목록·severity/from·to/search/sort 필터·Page 포맷)·`/{id}`(상세 봉투 `{report,counts,detail}`·404)·`/dashboard`(KPI 3종+최근5). summary=`result.summary.highlight` 파생, `type`·`service`는 Q-007로 null, search=JSON `json_value` LIKE, from/to·today는 KST 경계, 정렬 화이트리스트(임의 컬럼 정렬 차단). curl 8종 검증. 커밋 `93d614a`
- 11:05 **내부 리포트 저장 API `POST /api/internal/reports` 구현·E2E 검증 (SVC-01, 슬라이스 1.5)** — 번들+결과를 report+3종 한 트랜잭션 저장, `trigger_time` UNIQUE 멱등(재전송 409 DUPLICATE_TRIGGER), 필수·timestamp 형식 검증(422 INVALID_PAYLOAD), 공통 에러 봉투 `{error:{code,message}}`. timestamp는 ISO`Z`·공백형 둘 다 수용(api-spec §6 쟁점6 미결 대응). curl로 201/409/422·양 형식 UTC 저장(ms 보존)·빈 service `""` 전량 확인. 커밋 `259a59f`
- 10:50 **크로스컷 수정 2건 (저장 검증 중 발견)** — ① Boot4=Jackson3인데 Hibernate7 JSON 매퍼는 Jackson2 전제 → `FormatMapper`를 Jackson3(tools.jackson)로 직접 구현해 주입 ② JVM 기본 TZ(KST)와 커넥션 UTC 불일치로 `LocalDateTime` −9h 밀림 → main에서 JVM UTC 고정(schema.sql UTC 계약 정합). 커밋 `178bfe1`
- 10:35 **JPA 엔티티 4종 + 레포지토리 (슬라이스 1.5 착수)** — report(JSON `trigger_info`·`result` 패스스루) + log/metric/trace(공통 골격 `@MappedSuperclass SignalRow`로 중복 제거). `ddl-auto=validate`로 schema.sql과 1:1 정합 확인 — 엔티티 없어 무검증이던 validate가 실검증으로 전환. 커밋 `52b62ee`
- 10:00 **MySQL 연결·Docker 배포 구성** — H2 제거 MySQL 단일화, application.yaml datasource+`ddl-auto=validate`, Dockerfile(멀티스테이지·non-root)+compose(mysql:8.4·schema.sql init·healthcheck·127.0.0.1 바인딩), `.env` 시크릿 분리. compose up 후 4테이블 생성·앱↔DB 연결 확인. 커밋 `6459bcb`·`f805781`

## 백로그 / 미해결

**7/8 마감 (7/7 회의 액션, 본인 담당)**
- [ ] (오전) API 연동 data schema 확정 — front↔Spring, FastAPI↔Spring (석진·혜림·가희)
- [ ] (오전) SDK↔FastAPI data schema의 데이터 모델 문서 포함 여부 논의·확정 (석진·예지)
- [ ] (오후) 데이터 모델 설계 문서 작성 (③)
- [ ] **연지에게 검토 요청 알리기** — MVP 정의서 v0.3·슬라이스 v0.4 산출물 DB '검토 요청' 전환 완료 (7/7 15:35). 메시지에 D-018 이슈보드 링크 포함 권장

**미해결**
- [ ] Q-007: 목록 아이템 `type`·`service` 원천 (api-spec §6 쟁점3) — schema.sql에 컬럼 없고 detail 5키에도 없음. Step 3(조회) 목록에서 마주침 → severity 완화(D-022) 선례 따라 우선 null/best-effort, 팀 확정 후 컬럼화 여부 결정 (`type`=LLM 판정 저장 경로, `service`=trigger에서 서비스명 제거로 파생 불가)
- [ ] Q-006: agent 구조(단일 vs 3종+종합) 결정 대기 (예지·가희, ~7/9) → 결정 시 MVP-정의서 §4·슬라이스 1.4·S2 갱신
- [ ] Q-005(svc_kill 트리거) 공식 확정 → MVP-정의서 §5·슬라이스 S4 취소선 정리 (Perf 분석과 일치, 근거 확보됨)
- [ ] S1 1.7(/ingest 최소버전) 담당 확정 — 잠정 고유경·박가희
- [ ] 검증표: 시나리오별 media 계열 기대 원인 서비스 이름 확정 (데이터 라벨 확인 후, Q-003)
- [ ] D5·D6 담당 충돌 확인 (Q-001·Q-002) — 실질 분담은 정리됨(유경=데이터 분석·트리거, 석진=스키마·API 계약·Spring), 문서상 공식 정리만 남음
- [ ] **D-018(실시간 view MVP 제외) 팀 구두 합의** — 이슈보드 등록 완료(7/7), 회의/채팅에서 확인만 받으면 종결. 화면 설계(D4, 혜림) 동기화 포함
- [ ] Notion 슬라이스 분해표 페이지 속성(버전 등) 기입 여부 확인

**완료**
- [x] ~~MVP-정의서.md 보강: POC=실시간 view 범위, 7/20 데드라인 매핑~~ → 7/7 v0.2 완료
- [x] ~~VAL-01 우선순위 확인~~ → 7/7 회의로 해소 (D-016)
