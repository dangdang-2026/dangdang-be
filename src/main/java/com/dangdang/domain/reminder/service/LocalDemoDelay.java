package com.dangdang.domain.reminder.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

//시연용 짧은 대기 - 로컬 프로필일 때만 이 빈이 생성. prod 같이 켜져있을땐 안생김.
@Component
@Profile("local & !prod")
public class LocalDemoDelay {

    private final Duration delay;

    public LocalDemoDelay(@Value("${reminder.test-delay}")Duration delay) {
        this.delay = delay;
    }

    public Duration delay(){
        return delay;
    }
}
