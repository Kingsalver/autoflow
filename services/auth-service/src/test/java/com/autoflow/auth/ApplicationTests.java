package com.autoflow.auth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context load test — requires Postgres, Redis, and Kafka to be running.
 * Run with: docker compose up -d, then mvn test -Dtest=ApplicationTests
 */
@SpringBootTest
@Disabled("Requires running infrastructure (docker compose up -d)")
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
