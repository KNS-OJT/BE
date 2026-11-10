package com.p03.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class P03InventoryApplication {

    public static void main(String[] args) {
        // Mọi thời gian trong hệ thống xử lý theo UTC (SDD mục 4); FE tự đổi sang giờ Việt Nam khi hiển thị.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(P03InventoryApplication.class, args);
    }

}
