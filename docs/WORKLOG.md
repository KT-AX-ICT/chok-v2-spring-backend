# WORKLOG

> 작업 기록. 최신이 위로. 형식: `- HH:MM 내용`
> **아카이브 규칙**: 당일 기준 이틀 이상 지난 날짜 섹션은 [`WORKLOG-ARCHIVE.md`](./WORKLOG-ARCHIVE.md)로 이동 (worklog 갱신 시마다 검사).

## 2026-07-10 (금)

- 11:20 **노션 🔌 API 명세서 페이지를 로컬 정본(7/10 개정)으로 전체 교체** — D-021·D-022 반영본으로 동기화(본문 replace + 비고 속성 갱신). 7/7 전례(부분 치환 이스케이프 실패)에 따라 전체 교체 방식. §4.1에 status 파라미터 불채택 명시 블록 추가. ⚠️ 기존 페이지의 인라인 discussion(§1.2·"분석 중"·§3.1·§4.2 4건)은 본문 교체로 앵커가 떨어졌을 수 있음 — 확인 필요. 로컬 docs = 노션 일치 상태
- 11:09 **노션 API 명세서(진행중 페이지) ↔ 로컬 대조 후 항목별 채택 확정 (D-022)** — 노션안 채택: `ts`→`timestamp` 전면 개명(DB 컬럼 `ts` 유지)·`window` `start/end`(`windowStart/End`)·목록 `type`=LLM 판정·후순위 `POST/GET /ingest/{id}`. 로컬안 채택: raw string(D-021)·`status` 쿼리 파라미터 불채택·쟁점 3 재작성·버전 라벨은 노션 기준 v0.2.1 유지(확정은 명일 회의). 추가 결정: **결과 PATCH 전부 DONE 처리**(ANALYZING·FAILED 후순위), severity 컬럼·계약만 신설(필수 검증 완화). api-spec·schema.md·schema.sql·vuerd 반영, Q-007 잔여 = type 전달 경로·값 체계·service 원천. ⚠️ 노션 쪽 낡은 곳(raw `{}`·`trigger.services[0]` 잔재·쟁점 3·§5.1 파생 문구) 동기화 필요
- 10:53 **"분석 중(OPEN/ANALYZING) 행 노출 = 확장 범위" 명시** — MVP는 DONE만 노출(v0.2.1)이라 severity·summary null 케이스 자체가 없음(현행 정합 이상 무). api-spec §2 목록 노트에 확장 범위 표기 + MVP-정의서 §11 확장 백로그 7번 신설(도입 시 null 계약·status 재노출 필요)
- 10:30 **/ingest trigger 2키 축소 + raw string 확정 반영 (D-021)** — trigger: `ts`→`timestamp` 개명, `signal` 제거, `services`→`modality` 개명(값 = 감지 모달리티 `log`/`metric`/`trace`, 서비스명 배제 — D-020 정답 유출 방지 확장). 자식 3종 `raw` JSON→TEXT(행당 원본 한 줄, RCA 분석은 FastAPI가 수신 번들로 수행·DB 미경유). api-spec **v0.3**(§2·3.1·4.2·5.1·쟁점 3), schema.md(ERD·골격 표·엔티티 String raw·T4), schema.sql, vuerd 4곳 동기 반영. ⚠️ **목록 `type`·`service` 원천 재확정 필요 → Q-007 등록** (trigger 파생 불가 — 후보: severity처럼 LLM 산출을 PATCH DONE에 포함). 유형(type) 확장 시 enum 아닌 유형 테이블 방침만 합의(지금 미도입)
- 10:30 WORKLOG 7/7 섹션 → `WORKLOG-ARCHIVE.md` 이동 (아카이브 규칙)

## 백로그 / 미해결

**7/8 마감 (7/7 회의 액션, 본인 담당)**
- [ ] (오전) API 연동 data schema 확정 — front↔Spring, FastAPI↔Spring (석진·혜림·가희)
- [ ] (오전) SDK↔FastAPI data schema의 데이터 모델 문서 포함 여부 논의·확정 (석진·예지)
- [ ] (오후) 데이터 모델 설계 문서 작성 (③)
- [ ] **연지에게 검토 요청 알리기** — MVP 정의서 v0.3·슬라이스 v0.4 산출물 DB '검토 요청' 전환 완료 (7/7 15:35). 메시지에 D-018 이슈보드 링크 포함 권장

**미해결**
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
