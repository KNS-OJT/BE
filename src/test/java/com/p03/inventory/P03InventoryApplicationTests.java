package com.p03.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Khởi động toàn bộ app trên database thật: kiểm tra entity khớp schema (ddl-auto=validate) và mọi endpoint
 * đều đã khai báo quyền (EndpointSecurityVerifier). Chỉ chạy khi có biến môi trường DB_HOST + JWT_SECRET
 * (máy dev đã cấu hình .env); CI không có database nên bỏ qua.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".{32,}")
class P03InventoryApplicationTests {

    @Test
    void contextLoads() {
    }
}
