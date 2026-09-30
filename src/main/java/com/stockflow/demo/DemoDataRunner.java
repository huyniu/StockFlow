package com.stockflow.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Nạp fixture khi khởi động với profile demo; không chạy trong cấu hình ứng dụng thông thường. */
@Component
@Profile("demo")
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    /** Nhận service qua proxy để transaction bao phủ toàn bộ lần nạp dữ liệu. */
    public DemoDataRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    /** Flyway và JPA đã khởi tạo trước runner; dữ liệu lỗi sẽ rollback thay vì để lại seed một phần. */
    @Override
    public void run(ApplicationArguments arguments) {
        seeder.seed();
    }
}
