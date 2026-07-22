-- 개발용 시드 데이터 — company·users. 멱등(재실행 안전, INSERT IGNORE + UNIQUE 키 기준)
-- 전제: 앱이 한 번 부팅해 Flyway V2까지 적용된 상태 (users/company 테이블 존재)
--
-- 실행 (PowerShell/bash 공통 — 파이프 금지: PowerShell 파이프는 한글을 ?로 깨뜨림):
--   docker compose cp scripts/seed-dev.sql db:/tmp/seed-dev.sql
--   docker compose exec db sh -c "mysql -uchokchok -pchokchok --default-character-set=utf8mb4 chokchok < /tmp/seed-dev.sql"
--
-- 모든 계정 비밀번호: chokchok1!  (BCrypt, Spring Security BCryptPasswordEncoder 호환 $2y)

INSERT IGNORE INTO company (company_code, company_name) VALUES
  ('KT001',  'KT'),
  ('CHOK01', '촉촉컴퍼니'),
  ('SN001',  'SocialNetwork');

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'admin@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', '관리자', 'ADMIN'
FROM company c WHERE c.company_code = 'KT001';

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'user1@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', '김촉촉', 'USER'
FROM company c WHERE c.company_code = 'KT001';

INSERT IGNORE INTO users (company_id, email, password_hash, name, role)
SELECT c.id, 'user2@chokchok.dev', '$2y$10$/6gOzp6NHrnJ5bDiIRhhUuz0Kfg3ggLclsBAOQHohlHDIbVY6zAn.', '박촉촉', 'USER'
FROM company c WHERE c.company_code = 'CHOK01';

-- 결과 확인
SELECT c.company_code, c.company_name, u.email, u.name, u.role
FROM users u JOIN company c ON c.id = u.company_id
ORDER BY c.company_code, u.email;
