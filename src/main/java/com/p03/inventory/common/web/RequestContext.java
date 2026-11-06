package com.p03.inventory.common.web;

import org.slf4j.MDC;

/**
 * Thông tin của request hiện tại, lưu trong MDC để: (1) log JSON tự có request_id, user_id;
 * (2) transaction manager truyền xuống database (app.request_id, app.client_ip) cho audit log.
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_USER_ID = "user_id";
    public static final String MDC_CLIENT_IP = "client_ip";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(MDC_REQUEST_ID);
    }

    public static String clientIp() {
        return MDC.get(MDC_CLIENT_IP);
    }
}
