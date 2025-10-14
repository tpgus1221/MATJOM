package com.matjom.matjom.common.config;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return new DateTimeProvider() {
            @Override
            public Optional<TemporalAccessor> getNow() {
                return Optional.of(OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
            }
        };
    }

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
