package com.matjom.matjom.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC(); // 9월 30일 최종: 전역에서 재사용할 UTC 기준 Clock
    }
}
