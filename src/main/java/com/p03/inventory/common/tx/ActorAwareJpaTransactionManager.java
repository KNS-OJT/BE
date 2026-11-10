package com.p03.inventory.common.tx;

import com.p03.inventory.common.security.CurrentUser;
import com.p03.inventory.common.web.RequestContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transaction manager của toàn ứng dụng. Ngay sau khi mở MỌI transaction, nó chạy:
 * <pre>
 * SELECT set_config('app.current_user_id', :userId, true),
 *        set_config('app.request_id',      :requestId, true),
 *        set_config('app.client_ip',       :clientIp, true)
 * </pre>
 * {@code true} = chỉ có hiệu lực trong transaction hiện tại (tương đương SET LOCAL), nên connection trả về pool
 * không mang theo người dùng cũ. Trigger database đọc {@code current_setting('app.current_user_id', true)} để
 * điền / kiểm tra người thực hiện và ghi audit (ADR-05, IA-IN05, IA-IN07).
 * <p>
 * Hệ quả cho các nhánh: chỉ cần {@code @Transactional} như bình thường, KHÔNG tự gọi set_config và KHÔNG
 * nhận createdBy / approvedBy / confirmedBy từ client. Người thực hiện lấy theo thứ tự:
 * {@link ActorContext} (nếu đang chạy runAs) → người dùng trong JWT → rỗng (trigger nghiệp vụ sẽ fail-closed).
 */
public class ActorAwareJpaTransactionManager extends JpaTransactionManager {

    private static final String SET_CONTEXT_SQL = """
            SELECT set_config('app.current_user_id', ?1, true),
                   set_config('app.request_id', ?2, true),
                   set_config('app.client_ip', ?3, true)
            """;

    public ActorAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            return;
        }
        EntityManager em = holder.getEntityManager();
        Long actor = ActorContext.current().orElseGet(CurrentUser::idOrNull);
        em.createNativeQuery(SET_CONTEXT_SQL)
                .setParameter(1, actor == null ? "" : actor.toString())
                .setParameter(2, nullToEmpty(RequestContext.requestId()))
                .setParameter(3, nullToEmpty(RequestContext.clientIp()))
                .getSingleResult();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
