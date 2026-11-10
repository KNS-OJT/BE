package com.p03.inventory.supplier;

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
import com.p03.inventory.supplier.dto.SupplierResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/suppliers qua chuỗi bảo mật thật (JWT filter → @PreAuthorize → GlobalExceptionHandler).
 * Chỉ giả lập phần đọc token / tải user và service. Supplier không có máy trạng thái nên không có ca 409.
 */
@WebMvcTest(SupplierController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ErrorResponseWriter.class, DbErrorTranslator.class})
class SupplierControllerTest {

    private static final String VALID_CREATE = """
            {"supplierName":"Công ty Văn phòng phẩm A","contactPerson":"Nguyễn Văn B",
             "phone":"+84 912345678","email":"sales@vpp-a.vn","address":"Quận 1, TP.HCM"}
            """;
    private static final String VALID_UPDATE = """
            {"supplierName":"Công ty Văn phòng phẩm A","contactPerson":null,
             "phone":null,"email":"sales@vpp-a.vn","address":null,"active":false}
            """;
    private static final SupplierResponse SAMPLE =
            new SupplierResponse(5L, "Công ty Văn phòng phẩm A", null, null, "sales@vpp-a.vn", null, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierService service;
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

    // ---- GET /api/suppliers ---------------------------------------------------------------

    @Test
    void list_purchasing_ok() throws Exception {
        when(service.search(eq("vpp"), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(SAMPLE), 0, 20, 1, 1));
        mvc.perform(as("tk-purchasing", get("/api/suppliers").param("q", "vpp")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].supplierName").value("Công ty Văn phòng phẩm A"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_manager_ok() throws Exception {
        when(service.search(isNull(), eq(false), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(as("tk-manager", get("/api/suppliers").param("isActive", "false")))
                .andExpect(status().isOk());
    }

    @Test
    void list_noToken_401() throws Exception {
        mvc.perform(get("/api/suppliers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @Test
    void list_warehouseOrRequester_403() throws Exception {
        for (String token : List.of("tk-warehouse", "tk-requester")) {
            mvc.perform(as(token, get("/api/suppliers")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void list_sizeOver100_400() throws Exception {
        mvc.perform(as("tk-purchasing", get("/api/suppliers").param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- GET /api/suppliers/{id} ----------------------------------------------------------

    @Test
    void get_ok() throws Exception {
        when(service.get(5L)).thenReturn(SAMPLE);
        mvc.perform(as("tk-manager", get("/api/suppliers/5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void get_missing_404() throws Exception {
        when(service.get(99L)).thenThrow(ApiException.notFound());
        mvc.perform(as("tk-purchasing", get("/api/suppliers/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void get_requester_403() throws Exception {
        mvc.perform(as("tk-requester", get("/api/suppliers/5"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    // ---- POST /api/suppliers --------------------------------------------------------------

    @Test
    void create_purchasing_201() throws Exception {
        when(service.create(any())).thenReturn(SAMPLE);
        mvc.perform(as("tk-purchasing", json(post("/api/suppliers"), VALID_CREATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void create_noToken_401() throws Exception {
        mvc.perform(json(post("/api/suppliers"), VALID_CREATE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void create_managerOrWarehouse_403() throws Exception {
        for (String token : List.of("tk-manager", "tk-warehouse", "tk-requester")) {
            mvc.perform(as(token, json(post("/api/suppliers"), VALID_CREATE)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void create_invalidEmail_400() throws Exception {
        // @Email chấp nhận "a@b" nhưng CHECK ck_suppliers_email của DB thì không → DTO chặn trước
        for (String email : List.of("a@b", "khong-phai-email", "a b@c.vn")) {
            mvc.perform(as("tk-purchasing", json(post("/api/suppliers"),
                            "{\"supplierName\":\"NCC\",\"email\":\"" + email + "\"}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("email"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void create_invalidPhoneOrBlankName_400() throws Exception {
        mvc.perform(as("tk-purchasing", json(post("/api/suppliers"), "{\"supplierName\":\"NCC\",\"phone\":\"abc\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("phone"));
        mvc.perform(as("tk-purchasing", json(post("/api/suppliers"), "{\"supplierName\":\"   \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("supplierName"));
        verifyNoInteractions(service);
    }

    @Test
    void create_unknownField_400() throws Exception {
        for (String field : List.of("\"active\":false", "\"id\":1", "\"createdBy\":1")) {
            mvc.perform(as("tk-purchasing", json(post("/api/suppliers"), "{\"supplierName\":\"NCC\"," + field + "}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        }
        verifyNoInteractions(service);
    }

    // ---- PUT /api/suppliers/{id} ----------------------------------------------------------

    @Test
    void update_purchasing_deactivate_ok() throws Exception {
        when(service.update(eq(5L), any())).thenReturn(new SupplierResponse(5L, "Công ty Văn phòng phẩm A",
                null, null, "sales@vpp-a.vn", null, false));
        mvc.perform(as("tk-purchasing", json(put("/api/suppliers/5"), VALID_UPDATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void update_manager_403() throws Exception {
        mvc.perform(as("tk-manager", json(put("/api/suppliers/5"), VALID_UPDATE))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void update_noToken_401() throws Exception {
        mvc.perform(json(put("/api/suppliers/5"), VALID_UPDATE)).andExpect(status().isUnauthorized());
    }

    @Test
    void update_missingActive_400() throws Exception {
        mvc.perform(as("tk-purchasing", json(put("/api/suppliers/5"), "{\"supplierName\":\"NCC\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("active"));
        verifyNoInteractions(service);
    }

    @Test
    void update_missing_404() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(ApiException.notFound());
        mvc.perform(as("tk-purchasing", json(put("/api/suppliers/99"), VALID_UPDATE)))
                .andExpect(status().isNotFound());
    }
}
