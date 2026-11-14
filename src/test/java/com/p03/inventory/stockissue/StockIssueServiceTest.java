package com.p03.inventory.stockissue;
import com.p03.inventory.common.error.*;
import com.p03.inventory.common.security.*;
import com.p03.inventory.product.*;
import com.p03.inventory.stockissue.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StockIssueServiceTest {
    StockIssueRepository issues;
    StockIssueItemRepository lines;
    ProductRepository products;
    StockIssueService service;
    @BeforeEach void setup() {
        issues=mock(StockIssueRepository.class); lines=mock(StockIssueItemRepository.class);
        products=mock(ProductRepository.class); service=new StockIssueService(issues,lines,products);
        login(1L,Role.REQUESTER);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login(Long id, Role role) {
        var p=new AppUserPrincipal(id,"test",role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.authorities()));
    }
    StockIssue issue(Long owner, StockIssueStatus status) {
        var s=new StockIssue(); s.setId(10L); s.setRequestedBy(owner); s.setStatus(status); return s;
    }
    @Test void cannotReadAnotherOwnersIssue() {
        when(issues.findById(10L)).thenReturn(Optional.of(issue(2L,StockIssueStatus.DRAFT)));
        var e=assertThrows(ApiException.class,()->service.get(10L));
        assertEquals(ErrorCode.NOT_FOUND,e.code());
        assertEquals(ApiException.AuditReason.NOT_OWNER,e.auditReason());
        verifyNoInteractions(lines);
    }
    @Test void cannotUpdateAnotherOwnersIssue() {
        when(issues.lockById(10L)).thenReturn(Optional.of(issue(2L,StockIssueStatus.DRAFT)));
        assertEquals(ErrorCode.NOT_FOUND,assertThrows(ApiException.class,
                ()->service.update(10L,new StockIssueUpdateRequest("test",List.of()))).code());
        verifyNoInteractions(lines,products);
    }
    @Test void cannotEditConfirmedIssue() {
        when(issues.lockById(10L)).thenReturn(Optional.of(issue(1L,StockIssueStatus.CONFIRMED)));
        assertEquals(ErrorCode.INVALID_STATE,assertThrows(ApiException.class,
                ()->service.update(10L,new StockIssueUpdateRequest("test",List.of()))).code());
        verifyNoInteractions(lines,products);
    }
    @Test void purchasingCanCreateIssue() {
        login(3L,Role.PURCHASING_STAFF);
        Product product=new Product(); product.setActive(true);
        when(products.findById(5L)).thenReturn(Optional.of(product));
        when(issues.saveAndFlush(any())).thenAnswer(inv -> {
            StockIssue issue=inv.getArgument(0);
            issue.setId(10L);
            return issue;
        });
        service.create(new StockIssueCreateRequest("test",
                List.of(new StockIssueLineRequest(5L,BigDecimal.ONE))));
        verify(issues).saveAndFlush(any());
    }
    @Test void requesterListUsesAuthenticatedOwner() {
        var pageable=PageRequest.of(0,20);
        when(issues.findByRequestedBy(1L,pageable)).thenReturn(Page.empty(pageable));
        assertTrue(service.list(null,pageable).items().isEmpty());
        verify(issues).findByRequestedBy(1L,pageable);
        verify(issues,never()).findAll(any(Pageable.class));
    }
    @Test void warehouseListCanSeeReviewQueue() {
        login(4L,Role.WAREHOUSE_STAFF);
        var pageable=PageRequest.of(0,20);
        when(issues.findAll(pageable)).thenReturn(Page.empty(pageable));
        assertTrue(service.list(null,pageable).items().isEmpty());
        verify(issues).findAll(pageable);
    }
    @Test void duplicateProductRejectedBeforeWriting() {
        var p=new Product(); p.setActive(true);
        when(products.findById(5L)).thenReturn(Optional.of(p));
        var line=new StockIssueLineRequest(5L,BigDecimal.ONE);
        assertEquals(ErrorCode.VALIDATION_ERROR,assertThrows(ApiException.class,
                ()->service.create(new StockIssueCreateRequest("test",List.of(line,line)))).code());
        verifyNoInteractions(issues,lines);
    }
    @Test void requesterCannotConfirmOwnIssue() {
        login(1L,Role.WAREHOUSE_STAFF);
        when(issues.lockById(10L)).thenReturn(Optional.of(issue(1L,StockIssueStatus.DRAFT)));
        var e=assertThrows(ApiException.class,()->service.confirm(10L));
        assertEquals(ErrorCode.SOD_VIOLATION,e.code());
        assertEquals(ApiException.AuditReason.SOD_VIOLATION,e.auditReason());
        verify(issues,never()).saveAndFlush(any());
    }
    @Test void warehouseConfirmsIssueAndUsesAuthenticatedActor() {
        login(9L,Role.WAREHOUSE_STAFF);
        StockIssue issue=issue(1L,StockIssueStatus.DRAFT);
        when(issues.lockById(10L)).thenReturn(Optional.of(issue));
        when(issues.saveAndFlush(issue)).thenReturn(issue);

        service.confirm(10L);

        assertEquals(StockIssueStatus.CONFIRMED,issue.getStatus());
        assertEquals(9L,issue.getConfirmedBy());
        verify(issues).saveAndFlush(issue);
    }
    @Test void warehouseRejectsIssueWithRequiredReason() {
        login(9L,Role.WAREHOUSE_STAFF);
        StockIssue issue=issue(1L,StockIssueStatus.DRAFT);
        when(issues.lockById(10L)).thenReturn(Optional.of(issue));
        when(issues.saveAndFlush(issue)).thenReturn(issue);

        service.reject(10L,new StockIssueDecisionRequest("  Not available  "));

        assertEquals(StockIssueStatus.CANCELED,issue.getStatus());
        assertEquals("Not available",issue.getCancelReason());
    }
    @Test void inactiveProductRejectedBeforeWriting() {
        var p=new Product(); p.setActive(false);
        when(products.findById(5L)).thenReturn(Optional.of(p));
        assertThrows(ApiException.class,()->service.create(new StockIssueCreateRequest("test",
                List.of(new StockIssueLineRequest(5L,BigDecimal.ONE)))));
        verifyNoInteractions(issues,lines);
    }
}
