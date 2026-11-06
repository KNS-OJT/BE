package com.p03.inventory.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Inject {@link Clock} thay vì gọi Instant.now() trực tiếp, để test điều khiển được thời gian (hạn JWT, khóa tài khoản). */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
