package com.p03.inventory.common.security;

import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "Mặc định từ chối" (SDD mục 1): khi khởi động, quét mọi handler của package com.p03.inventory.
 * Handler nào thiếu {@code @PreAuthorize} / {@code @PermitAll} (ở method hoặc class) → app KHÔNG khởi động,
 * kèm danh sách endpoint vi phạm. Nhờ vậy không nhánh nào lỡ để lọt endpoint không phân quyền vào main.
 */
@Component
public class EndpointSecurityVerifier implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EndpointSecurityVerifier.class);
    private static final String BASE_PACKAGE = "com.p03.inventory";

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, RequestMappingHandlerMapping> mappings =
                event.getApplicationContext().getBeansOfType(RequestMappingHandlerMapping.class);
        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (RequestMappingHandlerMapping mapping : mappings.values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
                HandlerMethod hm = e.getValue();
                if (!hm.getBeanType().getName().startsWith(BASE_PACKAGE)) {
                    continue; // bỏ qua handler của framework (springdoc, /error…)
                }
                checked++;
                if (!isSecured(hm)) {
                    violations.add(e.getKey() + " -> " + hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Các endpoint sau chưa khai báo quyền (@PreAuthorize(Authz.…) hoặc @PermitAll):\n  "
                    + String.join("\n  ", violations));
        }
        log.info("EndpointSecurityVerifier: {} endpoint đều đã khai báo quyền", checked);
    }

    static boolean isSecured(HandlerMethod hm) {
        return AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PermitAll.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), PermitAll.class);
    }
}
