package com.dangdang.global.config;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.filter.JwtAuthenticationFilter;
import com.dangdang.global.security.AuthErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthErrorWriter authErrorWriter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // 쿠키 SameSite + JSON API 라 끈다
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 화면 X
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable) // 로그아웃은 우리 API 로
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 X, 토큰만
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> authErrorWriter.write(res, AuthErrorCode.LOGIN_REQUIRED)) // 401
                        .accessDeniedHandler((req, res, ex) -> authErrorWriter.write(res, AuthErrorCode.ACCESS_DENIED))) // 403
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll() // 가입·로그인·재발급·로그아웃
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // 운영자 화면 (나중)
                        .anyRequest().authenticated()); // 나머지는 로그인해야
        return http.build();
    }
}
