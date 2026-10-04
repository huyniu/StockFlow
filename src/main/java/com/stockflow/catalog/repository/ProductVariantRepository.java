package com.stockflow.catalog.repository;

import com.stockflow.catalog.domain.ProductVariant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tra cứu ánh xạ trang sản phẩm ↔ SKU màu, giữ nguyên khóa ngoại tồn kho/đơn hàng cũ. */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** Tìm tất cả lựa chọn màu để sửa dưới khóa của cùng sản phẩm gốc. */
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);

    /** SKU chỉ thuộc một nhóm; dùng để chặn thao tác nhóm lồng nhau và màu đã dừng bán. */
    Optional<ProductVariant> findBySkuProductId(Long skuProductId);

    /** Một query cho cả trang SKU; không tìm cha từng sản phẩm gây N+1. */
    @Query("""
            SELECT variant
            FROM ProductVariant variant
            JOIN FETCH variant.product
            JOIN FETCH variant.skuProduct
            JOIN FETCH variant.version
            WHERE variant.skuProduct.id IN :ids
            """)
    List<ProductVariant> findMappings(@Param("ids") Collection<Long> ids);

    /** Database kiểm tra duy nhất; service kiểm tra sớm dưới khóa gốc để trả lỗi 409 dễ hiểu. */
    boolean existsByVersionIdAndColorKey(Long versionId, String colorKey);
}
