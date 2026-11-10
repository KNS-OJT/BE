package com.p03.inventory.supplier;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.supplier.dto.SupplierCreateRequest;
import com.p03.inventory.supplier.dto.SupplierResponse;
import com.p03.inventory.supplier.dto.SupplierUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository repo;
    @InjectMocks
    private SupplierService service;

    @Test
    void create_trimsAndStoresBlankAsNull_alwaysActive() {
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        SupplierResponse r = service.create(new SupplierCreateRequest("  NCC A  ", "   ", null,
                "sales@ncc-a.vn", ""));

        ArgumentCaptor<Supplier> saved = ArgumentCaptor.forClass(Supplier.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("NCC A");
        assertThat(saved.getValue().getContactPerson()).isNull();
        assertThat(saved.getValue().getAddress()).isNull();
        assertThat(r.active()).isTrue();
    }

    @Test
    void update_inactive_meansStopCooperation() {
        Supplier s = new Supplier();
        s.setId(5L);
        s.setName("NCC A");
        s.setActive(true);
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        when(repo.saveAndFlush(s)).thenReturn(s);

        SupplierResponse r = service.update(5L, new SupplierUpdateRequest("NCC A", null, null, null, null, false));

        assertThat(r.active()).isFalse();
        assertThat(s.isActive()).isFalse();
    }

    @Test
    void update_missing_isNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99L,
                new SupplierUpdateRequest("NCC", null, null, null, null, true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void get_missing_isNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
