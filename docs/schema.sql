-- RCA 저장 스키마 (MySQL 8.0+)
-- report(부모) 1 : N log / metric / trace
-- 3종 공통 골격: { report_id, ts, service, raw }
--   ts      = 정규화된 타임스탬프, UTC 고정 (로그 원본이 µs 정밀도라 DATETIME(3)로 ms 보존)
--   service = canonical service name
--   raw     = 원본 그대로 (무파싱 보관, 분석 단계에서만 해석)

CREATE TABLE report (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  bundle_id    VARCHAR(128) UNIQUE,             -- 엣지 생성 멱등 키 (재시도 dedupe, MVP-정의서 §7.3)
  title        VARCHAR(255),
  status       VARCHAR(32)  DEFAULT 'OPEN',     -- OPEN / ANALYZING / DONE / FAILED
  window_from  DATETIME(3),                     -- 조사 대상 시간 구간 시작 (UTC)
  window_to    DATETIME(3),                     -- 조사 대상 시간 구간 끝 (UTC)
  trigger_info JSON,                            -- 발화 신호 {ts, signal, services[]} — RCA 입력·상세 화면 표시용
  result       JSON,                            -- LLM 분석 결과 {summary, cause, impact, action} — FastAPI가 기록
  created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_log_report (report_id),
  INDEX idx_log_ts_svc (ts, service)
);

CREATE TABLE metric (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  -- 집계 쿼리 필요해지면 여기에 metric_name VARCHAR, value DOUBLE 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_metric_report (report_id),
  INDEX idx_metric_ts_svc (ts, service)
);

CREATE TABLE trace (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_id  BIGINT       NOT NULL,
  ts         DATETIME(3)  NOT NULL,
  service    VARCHAR(128) NOT NULL,
  raw        JSON         NOT NULL,
  -- span 단위 조회 필요해지면 여기에 trace_id VARCHAR, span_id, parent_span_id 추가
  FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE,
  INDEX idx_trace_report (report_id),
  INDEX idx_trace_ts_svc (ts, service)
);
