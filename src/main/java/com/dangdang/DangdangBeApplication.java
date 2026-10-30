package com.dangdang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DangdangBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(DangdangBeApplication.class, args);
	}

}
