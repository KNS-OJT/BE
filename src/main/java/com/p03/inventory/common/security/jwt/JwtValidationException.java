package com.p03.inventory.common.security.jwt;

import com.p03.inventory.common.error.ErrorCode;

/** Token không hợp lệ: {@link ErrorCode#TOKEN_EXPIRED} hoặc {@link ErrorCode#UNAUTHENTICATED}. */
public class JwtValidationException extends RuntimeException {

    private final ErrorCode code;

    public JwtValidationException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
