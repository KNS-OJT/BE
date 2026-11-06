package com.p03.inventory.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Khóa tài khoản sau N lần đăng nhập sai liên tiếp (SDD mục 6: 5 lần → khóa 15 phút). */
@ConfigurationProperties(prefix = "app.security")
public record LoginPolicyProperties(Integer maxFailedLogins, Duration lockDuration) {

    public LoginPolicyProperties {
        if (maxFailedLogins == null || maxFailedLogins < 1) {
            maxFailedLogins = 5;
        }
        if (lockDuration == null) {
            lockDuration = Duration.ofMinutes(15);
        }
    }
}
