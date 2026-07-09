# WORKLOG ARCHIVE

> [`WORKLOG.md`](./WORKLOG.md)에서 이틀 이상 지난 날짜 섹션을 이동 보관. 최신이 위로.

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
