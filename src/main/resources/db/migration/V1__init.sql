-- V1 baseline — RCA 저장 스키마 초기 생성 (Flyway). MySQL 8.4.9 LTS
-- report(부모) 1 : N log / metric / trace
-- 3종 공통 골격: { report_id, ts, service, raw }
--   ts      = 정규화된 타임스탬프, UTC 고정 (로그 원본이 µs 정밀도라 DATETIME(3)로 ms 보존)
--             전달 JSON 필드명은 timestamp (7/10 개명, D-022) — 컬럼명은 ts 유지, 매핑은 Spring 몫
--   service = canonical service name
--   raw     = 원본 그대로 (무파싱 TEXT — 행당 원본 한 줄, RCA 분석은 DB를 재조회하지 않음. D-021)

-- DDL 소유: Spring(chokchok) 단독. FastAPI는 DB에 직접 접근하지 않고 Spring 내부 API를 호출한다 (A안, 7/7 확정).
-- 스키마 SSOT = Flyway 마이그레이션(이 디렉터리). 앱 부팅 시 자동 적용되고 ddl-auto:validate가 엔티티↔테이블 정합을 검증한다.
--   → 적용 완료된 V1은 절대 수정 금지(체크섬 깨짐). 스키마 변경은 V2__*.sql 새 파일로 추가.
-- 인덱스 기준: 실제 쿼리 경로에만 건다 — 모든 자식 조회는 report_id 경유, 시간 필터는 report 내부에서만.

CREATE TABLE report (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  status       VARCHAR(32)  NOT NULL DEFAULT 'DONE',  -- DONE (7/14 통합 D-023: 분석 완료 후 저장이라 DONE 직생성). OPEN/ANALYZING/FAILED 상태기계는 후순위. 내부 전용 — 프론트 조회는 DONE만 (api-spec v0.2.1)
  severity     VARCHAR(16),                     -- HIGH / MID / LOW — 컬럼만 신설, 필수 검증 완화(D-022). LLM 판정 연동 후속, NULL 허용
  window_from  DATETIME(3),                     -- 조사 대상 시간 구간 시작 (UTC) — window.start
  window_to    DATETIME(3),                     -- 조사 대상 시간 구간 끝 (UTC) — window.end
  trigger_time DATETIME(3)  NOT NULL,           -- 트리거 발화 시각 (UTC) — trigger_info.trigger_time 승격 컬럼. 목록 detectedAt 원천 + 멱등키 (7/14 D-023)
  trigger_info JSON,                            -- 발화 신호 {trigger_time, triggered_by[]} (D-023) — triggered_by=감지 모달리티(log/metric/trace, 서비스명 아님)
  result       JSON,                            -- LLM 분석 결과 detail 5키 {rca, summary, evidence, impact, actions} — FastAPI가 POST로 저장 (7/14 통합 D-023)
  created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_report_trigger (trigger_time),  -- 멱등 (7/14 D-023): bundle_id 대체 — 같은 트리거 시각 재전송 → 409 DUPLICATE_TRIGGER. 콘텐츠 파생이라 재시도해도 동일 키. 재분석은 id기반 UPDATE라 새 INSERT 없음 → 단일키로 충분. NOT NULL 필수(UNIQUE는 NULL을 서로 다르게 취급)
  INDEX idx_report_status_created (status, created_at)  -- DONE 필터 + 최신순 페이징, dashboard 집계
  -- severity 필터·highCount는 이 인덱스의 DONE 범위 스캔으로 충분 (report 수백 건 수준) — 병목 시 (status, severity, created_at) 추가
  -- detectedAt 정렬(api-spec §4.1 화이트리스트)은 trigger_time 컬럼 직접 정렬 — JSON 추출 불필요
);

CREATE TABLE log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        TEXT         NOT NULL,
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_log_report_ts (report_id, ts)  -- report 단위 시간순 조회·counts. FK 인덱스 겸용(leftmost)
  -- 리포트 횡단 (ts, service) 글로벌 인덱스는 횡단 분석 쿼리가 생기면 그때 추가
);

CREATE TABLE metric (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        TEXT         NOT NULL,
  -- 집계 쿼리 필요해지면 여기에 metric_name VARCHAR, value DOUBLE 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_metric_report_ts (report_id, ts)
);

CREATE TABLE trace (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        TEXT         NOT NULL,
  -- span 단위 조회 필요해지면 여기에 trace_id VARCHAR, span_id, parent_span_id 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_trace_report_ts (report_id, ts)
);
