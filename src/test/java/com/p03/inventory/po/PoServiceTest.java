package com.p03.inventory.po;

import com.p03.inventory.common.error.ApiException;
import com.p03.inventory.common.error.ErrorCode;
import com.p03.inventory.common.security.AppUserPrincipal;
import com.p03.inventory.common.security.Role;
import com.p03.inventory.po.dto.PoCreateRequest;
import com.p03.inventory.po.dto.PoResponse;
import com.p03.inventory.po.dto.PoUpdateRequest;
import com.p03.inventory.supplier.Supplier;
import com.p03.inventory.supplier.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quy tắc nghiệp vụ PO ở tầng service (kiểm tra sớm). Trigger DB kiểm tra lại – xem báo cáo GATE 2. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PoServiceTest {

    private static final long PR_ID = 7L;
    private static final long OTHER_PR_ITEM = 900L;

    @Mock
    private PurchaseOrderRepository orders;
    @Mock
    private SupplierRepository suppliers;
    @Mock
    private PurchaseRequestReader prReader;
    @InjectMocks
    private PoService service;

    private Supplier activeSupplier;
    private Supplier inactiveSupplier;

    @BeforeEach
    void setUp() {
        actAs(11L, Role.PURCHASING_STAFF);
        activeSupplier = supplier(3L, true);
        inactiveSupplier = supplier(4L, false);
        when(suppliers.findById(3L)).thenReturn(Optional.of(activeSupplier));
        when(suppliers.findById(4L)).thenReturn(Optional.of(inactiveSupplier));
        when(suppliers.findById(99L)).thenReturn(Optional.empty());

        Map<Long, PurchaseRequestReader.PrItem> prItems = new LinkedHashMap<>();
        prItems.put(71L, new PurchaseRequestReader.PrItem(71L, 1L, new BigDecimal("10")));
        prItems.put(72L, new PurchaseRequestReader.PrItem(72L, 2L, new BigDecimal("5.5")));
        prItems.put(73L, new PurchaseRequestReader.PrItem(73L, 3L, new BigDecimal("2")));
        when(prReader.findItems(PR_ID)).thenReturn(prItems);
        when(prReader.findStatus(PR_ID)).thenReturn(Optional.of("APPROVED"));
        when(prReader.findProductIds(anyCollection())).thenReturn(Map.of(71L, 1L, 72L, 2L, 73L, 3L));
        when(orders.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- Tạo PO ---------------------------------------------------------------------------

    @Test
    void create_fromApprovedPr_isDraft_createdByCurrentUser() {
        PoResponse r = service.create(createReq(3L, item(71L, "10"), item(72L, "1.5")));

        ArgumentCaptor<PurchaseOrder> saved = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orders).saveAndFlush(saved.capture());
        PurchaseOrder po = saved.getValue();
        assertThat(po.getStatus()).isEqualTo(PoStatus.DRAFT);
        assertThat(po.getCreatedBy()).isEqualTo(11L);
        assertThat(po.getPurchaseRequestId()).isEqualTo(PR_ID);
        assertThat(po.getItems()).extracting(PurchaseOrderItem::getPurchaseRequestItemId).containsExactly(71L, 72L);
        assertThat(r.items()).extracting(PoResponse.Item::productId).containsExactly(1L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "REJECTED", "CANCELED"})
    void create_fromNotApprovedPr_isInvalidState409(String prStatus) {
        when(prReader.findStatus(PR_ID)).thenReturn(Optional.of(prStatus));
        assertCode(() -> service.create(createReq(3L, item(71L, "1"))), ErrorCode.INVALID_STATE);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void create_prMissing_is400() {
        when(prReader.findStatus(PR_ID)).thenReturn(Optional.empty());
        assertCode(() -> service.create(createReq(3L, item(71L, "1"))), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void create_prAlreadyHasPo_isDuplicate409() {
        when(orders.existsByPurchaseRequestId(PR_ID)).thenReturn(true);
        assertCode(() -> service.create(createReq(3L, item(71L, "1"))), ErrorCode.DUPLICATE);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void create_inactiveOrMissingSupplier_is400() {
        assertCode(() -> service.create(createReq(4L, item(71L, "1"))), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.create(createReq(99L, item(71L, "1"))), ErrorCode.VALIDATION_ERROR);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void create_prItemOfAnotherPr_is400() {
        assertCode(() -> service.create(createReq(3L, item(OTHER_PR_ITEM, "1"))), ErrorCode.VALIDATION_ERROR);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicatePrItemInRequest_is400() {
        assertCode(() -> service.create(createReq(3L, item(71L, "1"), item(71L, "2"))), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void create_quantityOverApproved_is400_butEqualIsOk() {
        assertCode(() -> service.create(createReq(3L, item(72L, "5.501"))), ErrorCode.VALIDATION_ERROR);
        service.create(createReq(3L, item(72L, "5.500")));
        verify(orders).saveAndFlush(any());
    }

    @Test
    void create_noItems_is400() {
        assertCode(() -> service.create(new PoCreateRequest(PR_ID, 3L, null, List.of())), ErrorCode.VALIDATION_ERROR);
    }

    // ---- Sửa PO ---------------------------------------------------------------------------

    @Test
    void update_draft_locksRow_andSyncsItemsByPrItem() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "10"), item(72L, "5"));
        PurchaseOrderItem keep = po.getItems().get(0);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));

        service.update(50L, updateReq(3L, uItem(71L, "4"), uItem(73L, "2")));

        verify(orders).findByIdForUpdate(50L);
        verify(orders, never()).findById(anyLong());
        assertThat(po.getItems()).extracting(PurchaseOrderItem::getPurchaseRequestItemId).containsExactly(71L, 73L);
        assertThat(po.getItems().get(0)).isSameAs(keep); // dòng cũ được giữ, không trỏ lại
        assertThat(keep.getQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void update_canChangeToAnotherActiveSupplier_butNotInactive() {
        Supplier other = supplier(8L, true);
        when(suppliers.findById(8L)).thenReturn(Optional.of(other));
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));

        assertCode(() -> service.update(50L, updateReq(4L, uItem(71L, "1"))), ErrorCode.VALIDATION_ERROR);
        assertThat(po.getSupplier()).isSameAs(activeSupplier);

        service.update(50L, updateReq(8L, uItem(71L, "1")));
        assertThat(po.getSupplier()).isSameAs(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDERED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELED"})
    void update_notDraft_isInvalidState409(String status) {
        PurchaseOrder po = po(50L, PoStatus.valueOf(status), activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        assertCode(() -> service.update(50L, updateReq(3L, uItem(71L, "2"))), ErrorCode.INVALID_STATE);
        assertThat(po.getItems().get(0).getQuantity()).isEqualByComparingTo("1");
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void update_emptyItems_is400_poKeepsItsLines() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        assertCode(() -> service.update(50L, new PoUpdateRequest(3L, null, List.of())), ErrorCode.VALIDATION_ERROR);
        assertThat(po.getItems()).hasSize(1);
    }

    @Test
    void update_prItemOfAnotherPr_is400() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        assertCode(() -> service.update(50L, updateReq(3L, uItem(OTHER_PR_ITEM, "1"))), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void update_missing_isNotFound() {
        when(orders.findByIdForUpdate(77L)).thenReturn(Optional.empty());
        assertCode(() -> service.update(77L, updateReq(3L, uItem(71L, "1"))), ErrorCode.NOT_FOUND);
    }

    // ---- Phát hành ------------------------------------------------------------------------

    @Test
    void order_draft_becomesOrdered_withRowLock() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));

        PoResponse r = service.order(50L);

        assertThat(r.status()).isEqualTo("ORDERED");
        verify(orders).findByIdForUpdate(50L);
        verify(orders).saveAndFlush(po);
    }

    @Test
    void order_twice_isInvalidState409() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        service.order(50L);
        assertCode(() -> service.order(50L), ErrorCode.INVALID_STATE);
    }

    @Test
    void order_supplierDeactivatedAfterDraft_is400() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, inactiveSupplier, item(71L, "1"));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        assertCode(() -> service.order(50L), ErrorCode.VALIDATION_ERROR);
        assertThat(po.getStatus()).isEqualTo(PoStatus.DRAFT);
    }

    @Test
    void order_withoutItems_is400() {
        PurchaseOrder po = po(50L, PoStatus.DRAFT, activeSupplier);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));
        assertCode(() -> service.order(50L), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void cancel_orderedPoStoresReasonAndChangesStatus() {
        PurchaseOrder po = po(50L, PoStatus.ORDERED, activeSupplier);
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(po));

        service.cancel(50L, new com.p03.inventory.po.dto.PoCancelRequest("  Supplier canceled  "));

        assertThat(po.getStatus()).isEqualTo(PoStatus.CANCELED);
        assertThat(po.getCancelReason()).isEqualTo("Supplier canceled");
        verify(orders).saveAndFlush(po);
    }

    // ---- Phạm vi xem ----------------------------------------------------------------------

    @Test
    void get_warehouseSeesDraft_isNotOwner404_withAudit() {
        actAs(21L, Role.WAREHOUSE_STAFF);
        when(orders.findById(50L)).thenReturn(Optional.of(po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"))));
        assertThatThrownBy(() -> service.get(50L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
            assertThat(e.auditReason()).isEqualTo(ApiException.AuditReason.NOT_OWNER);
            assertThat(e.entityType()).isEqualTo("PO");
            assertThat(e.entityId()).isEqualTo(50L);
        });
    }

    @Test
    void get_missing_isPlainNotFound_withoutAudit() {
        actAs(21L, Role.WAREHOUSE_STAFF);
        when(orders.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(404L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
            assertThat(e.auditReason()).isNull();
        });
    }

    @Test
    void get_warehouseSeesOrderedAndPartiallyReceived() {
        actAs(21L, Role.WAREHOUSE_STAFF);
        when(orders.findById(51L)).thenReturn(Optional.of(po(51L, PoStatus.ORDERED, activeSupplier, item(71L, "1"))));
        when(orders.findById(52L)).thenReturn(Optional.of(
                po(52L, PoStatus.PARTIALLY_RECEIVED, activeSupplier, item(71L, "1"))));
        assertThat(service.get(51L).status()).isEqualTo("ORDERED");
        assertThat(service.get(52L).status()).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    void get_managerAndPurchasingSeeDraft() {
        when(orders.findById(50L)).thenReturn(Optional.of(po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"))));
        assertThat(service.get(50L).status()).isEqualTo("DRAFT");
        actAs(41L, Role.MANAGER);
        assertThat(service.get(50L).status()).isEqualTo("DRAFT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_warehouse_onlyWaitingForReceipt() {
        actAs(21L, Role.WAREHOUSE_STAFF);
        PageRequest pageable = PageRequest.of(0, 20);
        when(orders.findByStatusIn(anyCollection(), any())).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(null, pageable);

        ArgumentCaptor<Collection<PoStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orders).findByStatusIn(statuses.capture(), any());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(PoStatus.ORDERED, PoStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void list_warehouseFilterDraft_isEmpty_withoutQuery() {
        actAs(21L, Role.WAREHOUSE_STAFF);
        var page = service.list(PoStatus.DRAFT, PageRequest.of(0, 20));
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verify(orders, never()).findByStatusIn(anyCollection(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_purchasing_allStatuses_orRequestedOne() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(orders.findByStatusIn(anyCollection(), any())).thenReturn(new PageImpl<>(
                List.of(po(50L, PoStatus.DRAFT, activeSupplier, item(71L, "1"))), pageable, 1));

        var page = service.list(null, pageable);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).items().get(0).productId()).isEqualTo(1L);

        service.list(PoStatus.DRAFT, pageable);
        ArgumentCaptor<Collection<PoStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orders, org.mockito.Mockito.times(2)).findByStatusIn(statuses.capture(), any());
        assertThat(statuses.getAllValues().get(0)).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PoStatus.class));
        assertThat(statuses.getAllValues().get(1)).containsExactly(PoStatus.DRAFT);
    }

    // ---- helpers --------------------------------------------------------------------------

    private static void actAs(Long id, Role role) {
        AppUserPrincipal p = new AppUserPrincipal(id, "u" + id, role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.authorities()));
    }

    private static Supplier supplier(Long id, boolean active) {
        Supplier s = new Supplier();
        s.setId(id);
        s.setName("NCC " + id);
        s.setActive(active);
        return s;
    }

    private record Itm(Long prItemId, BigDecimal qty) {
    }

    private static Itm item(Long prItemId, String qty) {
        return new Itm(prItemId, new BigDecimal(qty));
    }

    private static PoUpdateRequest.Item uItem(Long prItemId, String qty) {
        return new PoUpdateRequest.Item(prItemId, new BigDecimal(qty));
    }

    private static PoCreateRequest createReq(Long supplierId, Itm... items) {
        return new PoCreateRequest(PR_ID, supplierId, null,
                List.of(items).stream().map(i -> new PoCreateRequest.Item(i.prItemId(), i.qty())).toList());
    }

    private static PoUpdateRequest updateReq(Long supplierId, PoUpdateRequest.Item... items) {
        return new PoUpdateRequest(supplierId, null, List.of(items));
    }

    private static PurchaseOrder po(Long id, PoStatus status, Supplier supplier, Itm... items) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(id);
        po.setOrderCode("PO-2026-000" + id);
        po.setPurchaseRequestId(PR_ID);
        po.setSupplier(supplier);
        po.setCreatedBy(11L);
        po.setStatus(status);
        long itemId = id * 100;
        for (Itm i : items) {
            po.addItem(i.prItemId(), i.qty());
            po.getItems().get(po.getItems().size() - 1).setId(itemId++);
        }
        return po;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
