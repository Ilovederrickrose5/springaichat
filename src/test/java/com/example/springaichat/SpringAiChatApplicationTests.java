package com.example.springaichat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
// 依赖真实 MySQL(3307) / Redis(6379) / DashScope，本地或 CI 无中间件时无法通过。
// 集成测试将在后续任务用 Testcontainers 覆盖，这里直接跳过，避免制造假通过。
@Disabled("依赖真实 MySQL/Redis/DashScope；集成测试将在后续任务用 Testcontainers 覆盖")
class SpringAiChatApplicationTests {

	@Test
	void contextLoads() {
	}

}
