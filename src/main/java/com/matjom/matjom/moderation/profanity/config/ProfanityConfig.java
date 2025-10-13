package com.matjom.matjom.moderation.profanity.config;

import com.matjom.matjom.moderation.profanity.HardcodedProfanityFilter;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class ProfanityConfig {
    @Bean
    public ProfanityFilter profanityFilter() {
        return new HardcodedProfanityFilter();
    }
}
