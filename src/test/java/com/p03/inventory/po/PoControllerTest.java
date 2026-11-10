package com.p03.inventory.po;

import com.p03.inventory.common.audit.SecurityEventLogger;
import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.DbErrorTranslator;
import com.p03.inventory.common.error.ErrorResponseWriter;
import com.p03.inventory.common.paging.PageResponse;
import com.p03.inventory.common.security.AppUserPrincipal;
import com.p03.inventory.common.security.PrincipalLoader;
import com.p03.inventory.common.security.RestAccessDeniedHandler;
import com.p03.inventory.common.security.RestAuthenticationEntryPoint;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.common.security.SecurityConfig;
import com.p03.inventory.common.security.jwt.JwtService;
import com.p03.inventory.po.dto.PoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/po qua chuỗi bảo mật thật (JWT filter → @PreAuthorize → GlobalExceptionHandler / DbErrorTranslator).
 * Quy tắc nghiệp vụ chi tiết nằm ở PoServiceTest; ở đây kiểm tra quyền, validate DTO và ánh xạ lỗi → HTTP.
 */
@WebMvcTest(PoController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ErrorResponseWriter.class, DbErrorTranslator.class})
class PoControllerTest {

    private static final String CREATE = """
            {"purchaseRequestId":7,"supplierId":3,"expectedDeliveryDate":null,
             "items":[{"prItemId":71,"quantity":10},{"prItemId":72,"quantity":1.5}]}
            """;
    private static final String UPDATE = """
            {"supplierId":3,"expectedDeliveryDate":null,"items":[{"prItemId":71,"quantity":4}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PoService service;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private PrincipalLoader principalLoader;
    @MockitoBean
    private SecurityEventLogger securityEventLogger;

    @BeforeEach
    void users() {
        user("tk-purchasing", 11L, Role.PURCHASING_STAFF);
        user("tk-manager", 41L, Role.MANAGER);
        user("tk-warehouse", 21L, Role.WAREHOUSE_STAFF);
        user("tk-requester", 31L, Role.REQUESTER);
    }

    private void user(String token, Long id, Role role) {
        when(jwtService.parseUserId(token)).thenReturn(id);
        when(principalLoader.loadActive(id)).thenReturn(Optional.of(new AppUserPrincipal(id, token, role)));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static PoResponse po(String status) {
        return new PoResponse(50L, "PO-2026-00050", 7L, 3L, "NCC 3", 11L, LocalDate.of(2026, 11, 10), null,
                status, null, null, null, List.of(new PoResponse.Item(5001L, 71L, 1L, new BigDecimal("10"))));
    }

    // ---- POST /api/po ---------------------------------------------------------------------

    @Test
    void create_purchasing_201() throws Exception {
        when(service.create(any())).thenReturn(po("DRAFT"));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value(11));
    }

    @Test
    void create_noToken_401() throws Exception {
        mvc.perform(json(post("/api/po"), CREATE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void create_wrongRole_403_andAudited() throws Exception {
        for (String token : List.of("tk-manager", "tk-warehouse", "tk-requester")) {
            mvc.perform(as(token, json(post("/api/po"), CREATE)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        }
        verifyNoInteractions(service);
        verify(securityEventLogger, org.mockito.Mockito.times(3))
                .accessDenied(eq(ApiException.AuditReason.FORBIDDEN_ROLE), isNull(), isNull(), eq("POST /api/po"));
    }

    @Test
    void create_massAssignment_status_createdBy_orderCode_requesterId_400() throws Exception {
        for (String field : List.of("\"status\":\"ORDERED\"", "\"createdBy\":99", "\"orderCode\":\"PO-X\"",
                "\"requesterId\":5")) {
            String body = CREATE.trim().replaceFirst("^\\{", "{" + field + ",");
            mvc.perform(as("tk-purchasing", json(post("/api/po"), body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void create_invalidBody_400() throws Exception {
        List<String> bodies = List.of(
                "{\"purchaseRequestId\":7,\"supplierId\":3,\"items\":[]}",                                   // không có dòng
                "{\"purchaseRequestId\":7,\"supplierId\":3,\"items\":[{\"prItemId\":71,\"quantity\":0}]}",  // qty = 0
                "{\"purchaseRequestId\":7,\"supplierId\":3,\"items\":[{\"prItemId\":71,\"quantity\":1.2345}]}",
                "{\"supplierId\":3,\"items\":[{\"prItemId\":71,\"quantity\":1}]}",                           // thiếu PR
                "{\"purchaseRequestId\":7,\"supplierId\":3,\"expectedDeliveryDate\":\"2000-01-01\","
                        + "\"items\":[{\"prItemId\":71,\"quantity\":1}]}");                                  // ngày quá khứ
        for (String body : bodies) {
            mvc.perform(as("tk-purchasing", json(post("/api/po"), body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void create_prNotApproved_409() throws Exception {
        when(service.create(any())).thenThrow(ApiException.invalidState("Chỉ lập PO từ yêu cầu mua hàng đã duyệt"));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void create_duplicatePo_409() throws Exception {
        when(service.create(any())).thenThrow(ApiException.duplicate("Yêu cầu mua hàng này đã được lập PO"));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void create_duplicatePo_raceCaughtByDbUnique_409() throws Exception {
        // Hai request cùng qua kiểm tra sớm: UNIQUE purchase_request_id của DB chặn request thứ hai
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("x", new SQLException(
                "ERROR: duplicate key value violates unique constraint \"purchase_orders_purchase_request_id_key\"",
                "23505")));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Yêu cầu mua hàng này đã được lập PO"));
    }

    @Test
    void create_prNotApproved_raceCaughtByDbTrigger_409() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("x", new SQLException(
                "ERROR: IA-IN03/BR-02: purchase request 7 is SUBMITTED, only APPROVED requests can become a PO",
                "P0409")));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SUBMITTED"))));
    }

    @Test
    void create_supplierInactive_400() throws Exception {
        when(service.create(any())).thenThrow(ApiException.validation("Nhà cung cấp đã ngừng hợp tác"));
        mvc.perform(as("tk-purchasing", json(post("/api/po"), CREATE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- GET /api/po ----------------------------------------------------------------------

    @Test
    void list_allowedRoles_200() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageResponse<>(List.of(po("ORDERED")), 0, 20, 1, 1));
        for (String token : List.of("tk-purchasing", "tk-manager", "tk-warehouse")) {
            mvc.perform(as(token, get("/api/po")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].orderCode").value("PO-2026-00050"));
        }
    }

    @Test
    void list_requester_403() throws Exception {
        mvc.perform(as("tk-requester", get("/api/po"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void list_noToken_401() throws Exception {
        mvc.perform(get("/api/po")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_badStatusOrSize_400() throws Exception {
        mvc.perform(as("tk-purchasing", get("/api/po").param("status", "HACKED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(as("tk-purchasing", get("/api/po").param("size", "0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void list_passesStatusFilter() throws Exception {
        when(service.list(eq(PoStatus.ORDERED), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(as("tk-warehouse", get("/api/po").param("status", "ORDERED"))).andExpect(status().isOk());
        verify(service).list(eq(PoStatus.ORDERED), any());
    }

    // ---- GET /api/po/{id} -----------------------------------------------------------------

    @Test
    void get_ok() throws Exception {
        when(service.get(50L)).thenReturn(po("ORDERED"));
        mvc.perform(as("tk-warehouse", get("/api/po/50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(1));
    }

    @Test
    void get_requester_403() throws Exception {
        mvc.perform(as("tk-requester", get("/api/po/50"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    /**
     * Q3: PO ngoài phạm vi (notOwner) và PO không tồn tại (notFound) phải trả response GIỐNG HỆT nhau
     * (status, code, message, các trường) – chỉ requestId khác. Khác biệt duy nhất là audit phía server.
     */
    @Test
    void get_outOfScope_and_missing_returnIdenticalResponse() throws Exception {
        when(service.get(50L)).thenThrow(ApiException.notOwner("PO", 50L));
        when(service.get(404L)).thenThrow(ApiException.notFound());

        MvcResult outOfScope = mvc.perform(as("tk-warehouse", get("/api/po/50"))
                .header("X-Request-Id", "req-same-0001")).andReturn();
        MvcResult missing = mvc.perform(as("tk-warehouse", get("/api/po/404"))
                .header("X-Request-Id", "req-same-0001")).andReturn();

        assertThat(outOfScope.getResponse().getStatus()).isEqualTo(404).isEqualTo(missing.getResponse().getStatus());
        String a = outOfScope.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String b = missing.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(a).contains("\"code\":\"NOT_FOUND\"").isEqualTo(b);

        verify(securityEventLogger).accessDenied(eq(ApiException.AuditReason.NOT_OWNER), eq("PO"), eq(50L), anyString());
        verify(securityEventLogger, never()).accessDenied(any(), any(), eq(404L), anyString());
    }

    // ---- PUT /api/po/{id} -----------------------------------------------------------------

    @Test
    void update_purchasing_200() throws Exception {
        when(service.update(eq(50L), any())).thenReturn(po("DRAFT"));
        mvc.perform(as("tk-purchasing", json(put("/api/po/50"), UPDATE))).andExpect(status().isOk());
    }

    @Test
    void update_wrongRole_403_noToken_401() throws Exception {
        for (String token : List.of("tk-manager", "tk-warehouse", "tk-requester")) {
            mvc.perform(as(token, json(put("/api/po/50"), UPDATE))).andExpect(status().isForbidden());
        }
        mvc.perform(json(put("/api/po/50"), UPDATE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void update_emptyItems_or_unknownField_400() throws Exception {
        mvc.perform(as("tk-purchasing", json(put("/api/po/50"), "{\"supplierId\":3,\"items\":[]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("items"));
        mvc.perform(as("tk-purchasing", json(put("/api/po/50"),
                        "{\"supplierId\":3,\"status\":\"ORDERED\",\"items\":[{\"prItemId\":71,\"quantity\":1}]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(as("tk-purchasing", json(put("/api/po/50"),
                        "{\"supplierId\":3,\"purchaseRequestId\":8,\"items\":[{\"prItemId\":71,\"quantity\":1}]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(service);
    }

    @Test
    void update_orderedPo_409() throws Exception {
        when(service.update(eq(50L), any())).thenThrow(ApiException.invalidState("PO đang ORDERED"));
        mvc.perform(as("tk-purchasing", json(put("/api/po/50"), UPDATE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void update_missing_404() throws Exception {
        when(service.update(eq(77L), any())).thenThrow(ApiException.notFound());
        mvc.perform(as("tk-purchasing", json(put("/api/po/77"), UPDATE))).andExpect(status().isNotFound());
    }

    // ---- POST /api/po/{id}/order ----------------------------------------------------------

    @Test
    void order_purchasing_200() throws Exception {
        when(service.order(50L)).thenReturn(po("ORDERED"));
        mvc.perform(as("tk-purchasing", post("/api/po/50/order")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"));
    }

    @Test
    void order_wrongRole_403_noToken_401() throws Exception {
        for (String token : List.of("tk-manager", "tk-warehouse", "tk-requester")) {
            mvc.perform(as(token, post("/api/po/50/order"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/po/50/order")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void order_twice_409() throws Exception {
        when(service.order(50L)).thenThrow(ApiException.invalidState("PO đang ORDERED"));
        mvc.perform(as("tk-purchasing", post("/api/po/50/order")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void order_missing_404() throws Exception {
        when(service.order(77L)).thenThrow(ApiException.notFound());
        mvc.perform(as("tk-purchasing", post("/api/po/77/order"))).andExpect(status().isNotFound());
    }

    @Test
    void noStatusEndpoint_putStatus_isNotRouted() throws Exception {
        mvc.perform(as("tk-purchasing", json(put("/api/po/50/status"), "{\"status\":\"RECEIVED\"}")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
