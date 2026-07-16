# WORKLOG ARCHIVE

> [`WORKLOG.md`](./WORKLOG.md)에서 이틀 이상 지난 날짜 섹션을 이동 보관. 최신이 위로.

## 2026-07-10 (금)

- 11:20 **노션 🔌 API 명세서 페이지를 로컬 정본(7/10 개정)으로 전체 교체** — D-021·D-022 반영본으로 동기화(본문 replace + 비고 속성 갱신). 7/7 전례(부분 치환 이스케이프 실패)에 따라 전체 교체 방식. §4.1에 status 파라미터 불채택 명시 블록 추가. ⚠️ 기존 페이지의 인라인 discussion(§1.2·"분석 중"·§3.1·§4.2 4건)은 본문 교체로 앵커가 떨어졌을 수 있음 — 확인 필요. 로컬 docs = 노션 일치 상태
- 11:09 **노션 API 명세서(진행중 페이지) ↔ 로컬 대조 후 항목별 채택 확정 (D-022)** — 노션안 채택: `ts`→`timestamp` 전면 개명(DB 컬럼 `ts` 유지)·`window` `start/end`(`windowStart/End`)·목록 `type`=LLM 판정·후순위 `POST/GET /ingest/{id}`. 로컬안 채택: raw string(D-021)·`status` 쿼리 파라미터 불채택·쟁점 3 재작성·버전 라벨은 노션 기준 v0.2.1 유지(확정은 명일 회의). 추가 결정: **결과 PATCH 전부 DONE 처리**(ANALYZING·FAILED 후순위), severity 컬럼·계약만 신설(필수 검증 완화). api-spec·schema.md·schema.sql·vuerd 반영, Q-007 잔여 = type 전달 경로·값 체계·service 원천. ⚠️ 노션 쪽 낡은 곳(raw `{}`·`trigger.services[0]` 잔재·쟁점 3·§5.1 파생 문구) 동기화 필요
- 10:53 **"분석 중(OPEN/ANALYZING) 행 노출 = 확장 범위" 명시** — MVP는 DONE만 노출(v0.2.1)이라 severity·summary null 케이스 자체가 없음(현행 정합 이상 무). api-spec §2 목록 노트에 확장 범위 표기 + MVP-정의서 §11 확장 백로그 7번 신설(도입 시 null 계약·status 재노출 필요)
- 10:30 **/ingest trigger 2키 축소 + raw string 확정 반영 (D-021)** — trigger: `ts`→`timestamp` 개명, `signal` 제거, `services`→`modality` 개명(값 = 감지 모달리티 `log`/`metric`/`trace`, 서비스명 배제 — D-020 정답 유출 방지 확장). 자식 3종 `raw` JSON→TEXT(행당 원본 한 줄, RCA 분석은 FastAPI가 수신 번들로 수행·DB 미경유). api-spec **v0.3**(§2·3.1·4.2·5.1·쟁점 3), schema.md(ERD·골격 표·엔티티 String raw·T4), schema.sql, vuerd 4곳 동기 반영. ⚠️ **목록 `type`·`service` 원천 재확정 필요 → Q-007 등록** (trigger 파생 불가 — 후보: severity처럼 LLM 산출을 PATCH DONE에 포함). 유형(type) 확장 시 enum 아닌 유형 테이블 방침만 합의(지금 미도입)
- 10:30 WORKLOG 7/7 섹션 → `WORKLOG-ARCHIVE.md` 이동 (아카이브 규칙)

## 2026-07-07 (월)

- 17:26 **docs 커밋 + 노션 동기화로 마감** — chokchok 커밋 2건(`40e9b94` A안·internal API·인덱스 기준·구조 재배치, `5cc6151` ERD 추가). 노션 🧩 DB 스키마 설계서를 테이블 구조(ERD+DDL) 우선 순서로 전체 교체(부록 A/B 유지), 🔌 API 명세서도 A안 확정본으로 동기화(§5 internal API 2종, 체크리스트 저장주체 [x]). 로컬 docs = git = 노션 3곳 일치 상태로 퇴근
- 16:50 ERD 편집기용 `chokchok/docs/schema.vuerd.json` 생성 — VS Code ERD Editor(vuerd)로 열리는 4테이블+FK 3+인덱스 5 정의, schema.sql과 1:1 (수정 시 양쪽 동기화 주의)
- 16:40 schema.md 구조 재편(테이블 구조→인덱스 기준→분리 근거→개념→흐름→엔티티→테스트 순) + **ERD 추가** (Mermaid erDiagram — report 1:N log/metric/trace, 컬럼·제약·인덱스 주석 포함). ③ 데이터 모델 산출물의 "Spring 저장·조회 ERD" 요건 충족. D-019(저장 주체=Spring) DECISIONS 기록
- 16:20 **저장 주체 = Spring 확정(A안) 반영 + 정합성 기준 수립** — api-spec: §0 흐름 재작성(FastAPI→Spring internal API, DB 접근은 Spring 단독), §5 internal API 2종 신설(`POST /api/internal/reports` 저장·409 dedupe, `PATCH .../{id}` status·result — 이게 곧 I8), /ingest 에러 502로 조정. schema.sql: 인덱스 재정리 — 자식 3종 `(report_id, ts)` 복합으로 통일(글로벌 ts,service 제거·기준 명시), report `(status, created_at)` 추가, bundle_id UNIQUE KEY 명명. schema.md: §3.1 유니크·인덱스 기준 표 + §6 정합성 테스트 계획(Flyway V1 + ddl-auto=validate 장치, @DataJpaTest·Testcontainers T1~T6, H2 금지)
- 16:00 **chokchok/docs 스키마·API 스펙 리뷰 + 계약 초안 보강** — schema.sql `DATETIME(3)`+UTC·`bundle_id`(멱등)·`trigger_info` 추가, schema.md에 "엣지 내부 정규화(Parquet, 트리거용) ≠ 전달·저장 계약({ts,service,raw})" 구분 명시(회의 얇은/전체 정규화 질문의 답), api-spec.md에 ①저장 주체 결정 박스(FastAPI 직접 DB[현 스펙] vs Spring 저장 API[SVC-01·I8 정본] — **7/8 오전 확정 필요**) ②/ingest에 bundle_id·trigger 블록·409 ③counts 예시 현실화(317k→수백, NFR-01·02) ④§5 회의 확정 체크리스트 5건
- 15:35 **산출물 검토 요청 전환** — 산출물 DB의 MVP 정의서(버전 0.3)·슬라이스 분해표(버전 0.4) 상태를 '검토 요청'으로 변경 (📌최신본 링크는 기존에 연결됨). D-018(실시간 view 제외)은 이슈보드 등록: https://app.notion.com/p/3961bb95d18e81548666e92373b92256 (우선순위 높음 — 화면 설계 D4 동기화 포함). agent 구조는 현행 대기(멀티 최소버전 표기, ~7/9 결정 시 1.4·S2만 갱신). **연지에게 검토 요청 알리기만 남음**
- 15:20 노션 검토 페이지 부분 반영 실패 발견(마크다운 이스케이프 불일치로 치환 누락) → 두 페이지 모두 로컬 정본(v0.3/v0.4)으로 전체 교체 후 검증
- 15:05 **실시간 view MVP 제외 (D-018, D-002 뒤집음)** — 기획서 v0.2 확인 결과 실시간 분석 과정 표시의 근거가 기획서·FR·기능정의서·유스케이스 어디에도 없음(유일 근거 = 7/1 회의). MVP-정의서 §6을 제외 명시로 교체, 범위표·스토리·UI표·추적표에서 제거, 슬라이스 S5 삭제, 확장 백로그로 이동. ⚠️ 팀 공유 + 화면 설계(D4) 동기화 필요
- 14:48 **7/7 회의 결정 반영** — MVP-정의서 **v0.3**: 성공 기준에서 정확도 검증 분리(성공=E2E+프론트 조회, D-016), §5를 Gate3 검증 기준으로 재편, 트리거 신호를 고유경 Perf 분석 기준으로 구체화(cpu_contention=호스트 레벨·임계 초과 빈도/지속시간, code_stop=로그 부재+trace 500, 오염 신호 금지 명시). 슬라이스 **v0.4**: DoD S1·S3·S4 정확도→S6, "단일 에이전트"→멀티 최소버전(모달3+종합), I7·I8 7/20 전 당김(D-017). D-016·D-017·Q-006 기록, VAL-01 모순 해소
- 14:40 노션 7/7 회의록·산출물 DB·내부용 문서(고유경 Perf 에러 데이터분석) 확인 — 기획서·요구사항 피드백 5건과 회의 결정 사항 파악
- 11:15 로컬 신설 ID(API-07·UI-03·UC-21) 철회 (D-015) — 기능정의서·유스케이스에 없는 항목은 기준 문서에서 ID 발명 금지. POC 실시간 view는 MVP-정의서 §6 서술로만 유지(D-002 근거), 노션 반영 여부는 팀 논의로 전환. MVP-정의서·슬라이스 분해표·노션 검토 페이지 2건 동기 수정
- 11:02 슬라이스 분해표 v0.3 → 노션 **개인 페이지**(검토용 초안) 생성: https://app.notion.com/p/3961bb95d18e81198cefd90670a6d800 (MVP 정의서 검토 페이지와 상호 링크)
- 10:58 MVP-정의서 v0.2 → 노션 **개인 페이지**(검토용 초안) 생성: https://app.notion.com/p/3961bb95d18e8115b127f8924dc87681 — 팀장 승인 후 산출물 📌최신본으로 이관 예정
- 10:50 MVP-정의서 **v0.2** — 노션 기능정의서 v0.1(기능ID 23종)·유스케이스 v0.1(UC-01~20)과 정합한 단일 기준으로 재구성 (D-014). 엣지 SDK 아키텍처·전송(/ingest) 단계 반영, 범위표 기능ID 기준 재작성, POC 실시간 view 절 신설(API-07·UI-03·UC-21 로컬 신설), 7/20 역산 데드라인 매핑, svc_kill 트리거 실측 반영(Q-005 확정 대기), 부록 추적표(기능ID×UC×FR×MVP×슬라이스) 추가 → 작업가이드 ⑦ 보강 2건 완료
- 10:50 슬라이스 분해표 **v0.3** — S1에 1.7 번들 전송+/ingest 태스크 추가(EDGE-07·API-01, 담당 잠정), S4 트리거 표기를 로그 침묵+start log 2회로 수정, 슬라이스별 기능ID 표기, M/S/C↔P0~P2 매핑 규칙 명시
- 10:35 노션 기능정의서 v0.1·유스케이스 v0.1(7/7 새벽 업데이트) 대조 분석 — 어긋남 5건 발견: ①엣지 SDK 미반영 ②POC 실시간 view 노션 부재(역방향 갭) ③우선순위 3체계 혼재 ④svc_kill 트리거 표기 충돌 ⑤VAL-01 우선순위(S vs MVP 필수) 모호
- 10:35 WORKLOG 7/3 섹션 → `WORKLOG-ARCHIVE.md` 이동 (아카이브 규칙 첫 적용)

## 2026-07-03 (목)

- 15:57 MVP-정의서 §5 사용자·화면·예외(스토리·UI표·예외표 4행·권한) 추가 패치, §5~7→§6~8 재번호 → 로컬 + Notion 산출물 MVP 정의서 페이지 📌최신본 업로드 (D-012·D-013 추가)
- 15:56 AnoMod-data 파일 레벨 keep/drop 분석 → Notion 내부용 문서 DB 업로드. drop 3(NginxThrift_.log·metadata.txt·all_traces.json)+보류 1(jaeger_spans_rate). req_id 두 체계 실측 확인(log 내부 ID ≠ trace URL wrk2 ID)
- 15:54 오늘 데이터·스키마 작업 WORKLOG·DECISIONS 기록 (D-010·D-011·Q-005 추가)
- 15:45 정규화 스키마 설계서 v0.1 Notion 업로드 (03. 설계&기술 문서 > 내부용 문서 DB, 태그: 데이터)
- 15:52 WORKLOG·DECISIONS 통합관리 체계 생성
- 15:30 슬라이스 분해표 v0.2 — WBS 정합 (일정·담당 수정, WBS ID 매핑 추가). 로컬 + Notion 반영
- 15:00 WBS(01. 진행현황) 대조 → 담당·일정 불일치 발견. WBS의 D7·D8 담당 오류는 본인이 직접 수정 (→ 이석진)
- 14:30 슬라이스 분해표 v0.1 Notion 업로드 (산출물 > 슬라이스 분해표 페이지 📌최신본)
- 14:08 정규화 스키마 설계 v0.1 — `docs/normalization-schema.md` (3테이블 logs·metrics·spans, Parquet, canonical=trace 서비스명 체계). SN 실데이터 조사에서 svc_kill 신호가 trace error tag가 아닌 로그 침묵 104초+start log 2회로 판명 → D-003 상충, Q-005 등록
- 14:00 슬라이스 분해표 형식 보강 — 마일스톤·세부 태스크·예상 공수·진행 상태 추가
- 13:48 AnoMod 실데이터 확보 — 로컬 저장소(AnoMod-main)가 Git LFS 포인터뿐임을 발견, Zenodo에서 zip(202MB) 다운로드 후 SN 4개 폴더(3시나리오+normal)×3모달리티만 선별 해제 → `Dataset/AnoMod-data/` (232MB). zip은 TT 확장용으로 보존
- 13:30 `슬라이스-분해표.md` 초안 작성 (S0~S6, walking skeleton 방식)
- 13:00 `이석진-작업가이드.md` 작성 — 배분 브리핑 기준 담당 4종(③④⑦⑧) 정리. 기존 MVP-정의서.md에 POC 실시간 view·7/20 데드라인 누락 발견
- 12:00 `MVP-정의서.md` 초안 작성 (DoD·시나리오별 검수 기준표·판정 규칙)
- 11:00 `README.md` 작성 — 기획서 v0.2 + 요구사항 정의서 v0.2 통합 (Notion 정본 링크 포함)
