package com.stockflow.catalog.service;

import com.stockflow.catalog.dto.ProductAvailabilityResponse;
import com.stockflow.catalog.repository.ProductAvailabilityRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cung cấp tín hiệu còn hàng cho storefront; reserve nguyên tử vẫn quyết định khi khách đặt đơn. */
@Service
public class ProductAvailabilityService {

    private final ProductRepository products;
    private final ProductAvailabilityRepository availability;

    /** Tách truy vấn công khai khỏi API quản trị tồn kho để giữ nguyên phân quyền hiện có. */
    public ProductAvailabilityService(
            ProductRepository products, ProductAvailabilityRepository availability) {
        this.products = products;
        this.availability = availability;
    }

    /** Đọc nhất quán trong transaction, không khóa hàng, giữ hàng hoặc tạo movement khi duyệt sản phẩm. */
    @Transactional(readOnly = true)
    public List<ProductAvailabilityResponse> findAvailability(Long productId) {
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
        }
        return availability.findAvailability(productId).stream()
                .map(row -> new ProductAvailabilityResponse(
                        row.getProductId(), row.getWarehouseId(), row.getWarehouseCode(),
                        row.getWarehouseName(), row.getInStock()))
                .toList();
    }
}
