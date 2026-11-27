package com.dangdang.global.config;

import com.dangdang.global.filter.AccessLogFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class WebFilterConfig {

    // 접속 로그 필터를 제일 바깥(최우선)에 둔다 → 보안 필터까지 다 지난 뒤의 최종 상태코드를 찍기 위해
    // @Component를 안 달고 여기서만 등록한다 (AdminIpFilter와 같은 이유: 서블릿 필터 중복 등록 방지)
    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        FilterRegistrationBean<AccessLogFilter> bean = new FilterRegistrationBean<>(new AccessLogFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
