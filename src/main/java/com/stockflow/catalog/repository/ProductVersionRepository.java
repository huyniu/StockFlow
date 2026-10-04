package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lưu các phiên bản trong transaction khóa model; không thao tác tồn kho hoặc lịch sử đơn. */
public interface ProductVersionRepository extends JpaRepository<ProductVersion, Long> {

    /** Kiểm tra sớm để phản hồi 409; UNIQUE database vẫn bảo vệ ghi ngoài ứng dụng. */
    boolean existsByProductIdAndNameKey(Long productId, String nameKey);
}
