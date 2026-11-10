package com.p03.inventory.common.tx;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Thay transaction manager mặc định của Spring Boot bằng bản tự truyền người thực hiện xuống database.
 * Isolation mặc định của PostgreSQL là READ COMMITTED – đúng ADR-03, chống race bằng khóa dòng (FOR UPDATE).
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new ActorAwareJpaTransactionManager(emf);
    }
}
