-- 개발용 시드 데이터 — company·users. 멱등(재실행 안전, INSERT IGNORE + UNIQUE 키 기준)
-- 전제: 앱이 한 번 부팅해 Flyway V2까지 적용된 상태 (users/company 테이블 존재)
--
-- 실행 (PowerShell/bash 공통 — 파이프 금지: PowerShell 파이프는 한글을 ?로 깨뜨림):
--   docker compose cp scripts/seed-dev.sql db:/tmp/seed-dev.sql
--   docker compose exec db sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 "$MYSQL_DATABASE" < /tmp/seed-dev.sql'
--
-- 모든 계정 비밀번호: chokchok1!  (BCrypt, Spring Security BCryptPasswordEncoder 호환 $2y)

-- 회사: SN001·TT001 = 모니터링 대상 벤치마크 시스템(AnoMod, company_name은 코드 접두사 SN001→SN·TT001→TT).
--       CHOK = 운영사(chokchok 자체) — 어드민 소속. company_code는 ingest로 들어오는 값 형태.
-- TT는 report 데이터 도착 전 회사 행만 선반영.
INSERT IGNORE INTO company (company_code, company_name) VALUES
  ('SN001', 'SN'),
  ('TT001', 'TT'),
  ('CHOK',  '촉촉 주식회사');

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'admin@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', '관리자', 'ADMIN'
FROM company c WHERE c.company_code = 'CHOK';

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'sn.user@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', 'SN 사용자', 'USER'
FROM company c WHERE c.company_code = 'SN001';

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'tt.user@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', 'TT 사용자', 'USER'
FROM company c WHERE c.company_code = 'TT001';

-- 결과 확인
SELECT c.company_code, c.company_name, u.email, u.name, u.role
FROM users u JOIN company c ON c.id = u.company_id
ORDER BY c.company_code, u.email;
