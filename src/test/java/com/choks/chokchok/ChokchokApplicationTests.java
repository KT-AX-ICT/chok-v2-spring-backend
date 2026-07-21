package com.choks.chokchok;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@Testcontainers
class ChokchokApplicationTests {

	// 프로덕션과 동일한 MySQL 8.4를 임의 포트로 자체 기동. @ServiceConnection이 접속정보를 자동 주입 → 3306/3307·env 불필요
	// DDL 소유는 db/schema.sql 단독(compose와 동일 파일 재사용) — 컨테이너 init 디렉터리에 넣어 부팅 시 주입, validate가 정합 검증
	@Container
	@ServiceConnection
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
			.withCopyFileToContainer(
					MountableFile.forHostPath("db/schema.sql"),
					"/docker-entrypoint-initdb.d/schema.sql");

	@Test
	void contextLoads() {
	}

}
