package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// allowedIps: 운영자 API(/api/admin/**)를 받아 줄 공인 IP. 운영진이 쓰는 와이파이의 IP를 적는다
// 비어 있으면 IP는 검사하지 않는다 (로컬, 개발 서버). 그래도 CORS와 ADMIN 권한 확인은 그대로 막는다
@ConfigurationProperties("admin")
public record AdminIpProperties(List<String> allowedIps) {
    public AdminIpProperties {
        allowedIps = allowedIps == null ? List.of()
                : allowedIps.stream().map(String::strip).filter(ip -> !ip.isEmpty()).toList();
    }

    public boolean enabled() {
        return !allowedIps.isEmpty();
    }
}
