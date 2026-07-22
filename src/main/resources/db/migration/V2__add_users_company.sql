-- V2 — 로그인 계정(users)·기업(company) 추가 + report 회사 연결.
-- user N:1 company (users.company_id FK) · report N:1 company (report.company_code FK → company.company_code)
-- 회사 삭제가 계정/리포트를 조용히 지우면 안 되므로 RESTRICT (V1 CASCADE는 report→자식 데이터행 관계라 성격이 다름)

CREATE TABLE company (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_code VARCHAR(32)  NOT NULL,
  company_name VARCHAR(255) NOT NULL,
  UNIQUE KEY uq_company_code (company_code)
);

CREATE TABLE users (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id    BIGINT       NOT NULL,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  role          VARCHAR(32)  NOT NULL DEFAULT 'USER',
  created_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_users_email (email),
  FOREIGN KEY (company_id) REFERENCES company(id) ON DELETE RESTRICT
);

-- report 소속 회사: 사람이 읽는 코드를 직접 저장 + company.company_code(UNIQUE)에 FK (A안, 자동 생성 없음)
-- 코드 도입 전(V1 시절) 리포트는 회사 정보가 없으므로 NULL 허용
ALTER TABLE report
  ADD COLUMN company_code VARCHAR(32) NULL AFTER id,
  ADD FOREIGN KEY (company_code) REFERENCES company(company_code) ON DELETE RESTRICT;
