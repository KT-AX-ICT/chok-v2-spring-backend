package com.choks.chokchok;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ChokchokApplicationTests {

	// 프로덕션과 동일한 MySQL 8.4를 임의 포트로 자체 기동. @ServiceConnection이 접속정보를 자동 주입 → 3306/3307·env 불필요
	// 스키마는 Flyway가 컨텍스트 로딩 시 자동 적용(V1) → validate가 엔티티↔테이블 정합 검증
	@Container
	@ServiceConnection
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

	@Test
	void contextLoads() {
	}

}
