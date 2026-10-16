package com.dangdang.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI dangdangOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("당당 API")
						.description("임신성 당뇨 기록·알림 서비스 당당의 백엔드 API")
						.version("v1"))
				.servers(List.of(
						new Server().url("https://dang-dang.site").description("운영"),
						new Server().url("http://localhost:8080").description("로컬")));
	}
}
