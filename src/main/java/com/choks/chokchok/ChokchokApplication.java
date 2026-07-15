package com.choks.chokchok;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChokchokApplication {

	public static void main(String[] args) {
		// 시스템 전체가 UTC 고정 계약 — JVM 기본 타임존을 UTC로 못박아
		// LocalDateTime이 MySQL(serverTimezone=UTC)에 오프셋 변환 없이 저장되게 한다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ChokchokApplication.class, args);
	}

}
