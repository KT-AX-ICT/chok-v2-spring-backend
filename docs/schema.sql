-- RCA 저장 스키마 (MySQL 8.0+)
-- report(부모) 1 : N log / metric / trace
-- 3종 공통 골격: { report_id, ts, service, raw }
--   ts      = 정규화된 타임스탬프, UTC 고정 (로그 원본이 µs 정밀도라 DATETIME(3)로 ms 보존)
--   service = canonical service name
--   raw     = 원본 그대로 (무파싱 보관, 분석 단계에서만 해석)

-- DDL 소유: Spring(chokchok) 단독. FastAPI는 DB에 직접 접근하지 않고 Spring 내부 API를 호출한다 (A안, 7/7 확정).
-- 인덱스 기준: 실제 쿼리 경로에만 건다 — 모든 자식 조회는 report_id 경유, 시간 필터는 report 내부에서만.

CREATE TABLE report (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bundle_id    VARCHAR(128) NOT NULL,           -- 엣지 생성 멱등 키 (재시도 dedupe, MVP-정의서 §7.3)
  title        VARCHAR(255),
  status       VARCHAR(32)  NOT NULL DEFAULT 'OPEN',  -- OPEN / ANALYZING / DONE / FAILED
  window_from  DATETIME(3),                     -- 조사 대상 시간 구간 시작 (UTC)
  window_to    DATETIME(3),                     -- 조사 대상 시간 구간 끝 (UTC)
  trigger_info JSON,                            -- 발화 신호 {ts, signal, services[]} — RCA 입력·상세 화면 표시용
  result       JSON,                            -- LLM 분석 결과 {summary, cause, impact, action} — FastAPI가 PATCH로 요청, Spring이 기록
  created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_report_bundle (bundle_id),          -- 멱등: 같은 번들 재전송 → 409 (기존 report_id 반환)
  INDEX idx_report_status_created (status, created_at)  -- 목록 status 필터 + 최신순 페이징, dashboard 집계
);

CREATE TABLE log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_log_report_ts (report_id, ts)  -- report 단위 시간순 조회·counts. FK 인덱스 겸용(leftmost)
  -- 리포트 횡단 (ts, service) 글로벌 인덱스는 횡단 분석 쿼리가 생기면 그때 추가
);

CREATE TABLE metric (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  -- 집계 쿼리 필요해지면 여기에 metric_name VARCHAR, value DOUBLE 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_metric_report_ts (report_id, ts)
);

CREATE TABLE trace (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  -- span 단위 조회 필요해지면 여기에 trace_id VARCHAR, span_id, parent_span_id 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_trace_report_ts (report_id, ts)
);
