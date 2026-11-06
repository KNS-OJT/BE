package com.p03.inventory.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** Thông báo và SQLSTATE lấy đúng từ 01_schema.sql khi chạy bằng p03_app. */
class DbErrorTranslatorTest {

    private final DbErrorTranslator translator = new DbErrorTranslator();

    private DbErrorTranslator.Translated tr(String state, String message) {
        return translator.translate(new DataIntegrityViolationException("x", new SQLException(message, state)));
    }

    @Test
    void insufficientStock() {
        var t = tr("P0409", "ERROR: BR-05/IA-IN04: insufficient stock for product 3 (requested 20, available 10)");
        assertThat(t.code()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(t.message()).contains("BR-05/IA-IN04").doesNotContain("available");
    }

    @Test
    void overReceipt() {
        assertThat(tr("P0409", "BR-06/BR-21: receiving 5 would exceed ordered quantity 3").code())
                .isEqualTo(ErrorCode.OVER_RECEIPT);
    }

    @Test
    void invalidState() {
        assertThat(tr("P0409", "BR-16: invalid stock issue transition CONFIRMED -> DRAFT").code())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void selfApproval_isSod() {
        var t = tr("42501", "IA-IN01/BR-08: the requester cannot approve or reject their own purchase request");
        assertThat(t.code()).isEqualTo(ErrorCode.SOD_VIOLATION);
        assertThat(t.auditReason()).isEqualTo(ApiException.AuditReason.SOD_VIOLATION);
    }

    @Test
    void selfConfirmCheck_isSod() {
        assertThat(tr("23514", "new row for relation \"stock_issues\" violates check constraint \"ck_si_no_self_confirm\"").code())
                .isEqualTo(ErrorCode.SOD_VIOLATION);
    }

    @Test
    void missingActor_isForbidden() {
        var t = tr("42501", "IA-IN07: app.current_user_id is not set");
        assertThat(t.code()).isEqualTo(ErrorCode.FORBIDDEN_ROLE);
        assertThat(t.auditReason()).isEqualTo(ApiException.AuditReason.FORBIDDEN_ROLE);
    }

    @Test
    void duplicateSku_friendlyMessage() {
        var t = tr("23505", "duplicate key value violates unique constraint \"ux_products_sku\"");
        assertThat(t.code()).isEqualTo(ErrorCode.DUPLICATE);
        assertThat(t.message()).contains("SKU");
    }

    @Test
    void checkViolation_isValidation() {
        assertThat(tr("23514", "new row for relation \"products\" violates check constraint \"ck_products_sku_format\"").code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void unknown_isInternal() {
        assertThat(tr("XX000", "something broke").code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(translator.translate(new RuntimeException("no sql")).code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
