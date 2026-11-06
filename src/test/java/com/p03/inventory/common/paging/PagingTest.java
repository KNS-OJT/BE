package com.p03.inventory.common.paging;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagingTest {

    @Test
    void defaults() {
        Pageable p = Paging.of(null, null);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
    }

    @Test
    void sizeOver100_isValidationError() {
        assertThatThrownBy(() -> Paging.of(0, 101))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void negativePage_isValidationError() {
        assertThatThrownBy(() -> Paging.of(-1, 10)).isInstanceOf(ApiException.class);
    }
}
