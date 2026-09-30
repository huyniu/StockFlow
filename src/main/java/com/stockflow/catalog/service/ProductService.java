package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.dto.CreateProductRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductRequest;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý sản phẩm: tạo, cập nhật, xem chi tiết và tìm kiếm có phân trang.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Inject repository sản phẩm và danh mục để xử lý các ràng buộc giữa product và category.
     */
    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Tìm sản phẩm theo phân trang và filter tùy chọn theo category/status.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Long categoryId, ProductStatus status, Pageable pageable) {
        return productRepository.findAll(buildFilter(categoryId, status), pageable)
                .map(ProductResponse::from);
    }

    /**
     * Lấy chi tiết một sản phẩm theo id, trả 404 nếu không tồn tại.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        return ProductResponse.from(product);
    }

    /**
     * Tạo sản phẩm mới, chỉ được gọi bởi ADMIN ở controller. SKU phải duy nhất và category phải tồn tại.
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU sản phẩm đã tồn tại.");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục sản phẩm."));

        Product product = productRepository.save(new Product(
                category,
                sku,
                request.name().trim(),
                request.unitPrice(),
                request.status()));
        return ProductResponse.from(product);
    }

    /**
     * Cập nhật tên, giá và trạng thái sản phẩm theo semantics PATCH; field null thì giữ nguyên.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));

        product.update(
                request.name() == null ? null : request.name().trim(),
                request.unitPrice(),
                request.status());
        return ProductResponse.from(product);
    }

    /**
     * Xây dựng Specification động để query chỉ thêm điều kiện khi client thật sự truyền filter.
     */
    private Specification<Product> buildFilter(Long categoryId, ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
