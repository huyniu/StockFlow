package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.domain.ProductVariant;
import com.stockflow.catalog.domain.ProductVersion;
import com.stockflow.catalog.dto.CreateProductVariantRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductVariantRequest;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.ProductVariantRepository;
import com.stockflow.catalog.repository.ProductVersionRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý SKU phiên bản/màu dưới khóa model; giữ nguyên các liên kết tồn kho và lịch sử cũ.
 * Giá/ảnh/trạng thái thuộc SKU, thông số hiệu lực được ghép theo phiên bản đã chọn.
 */
@Service
public class ProductVariantService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductVersionRepository versions;

    /** Nhận repository để tạo nhóm màu và SKU trong cùng một transaction. */
    public ProductVariantService(
            ProductRepository products, ProductVariantRepository variants, ProductVersionRepository versions) {
        this.products = products;
        this.variants = variants;
        this.versions = versions;
    }

    /**
     * ADMIN thêm màu; SKU mới có tồn kho bằng 0 cho đến khi nhập hàng.
     * Lần đầu phải khai báo màu hiện tại, giữ nguyên SKU, giá, ảnh và lịch sử của nó.
     */
    @Transactional
    public ProductResponse create(Long productId, CreateProductVariantRequest request) {
        Product parent = lockRoot(productId);
        if (parent.getStatus() != ProductStatus.ACTIVE) {
            throw new ConflictException("Hãy kích hoạt sản phẩm trước khi thêm màu mới.");
        }
        List<ProductVariant> existing = parent.getVariants();
        if (existing.size() >= 100) {
            throw new BadRequestException("Một model chỉ được khai báo tối đa 100 SKU phiên bản/màu.");
        }
        String color = request.colorName().strip();
        String sku = request.sku().strip().toUpperCase(Locale.ROOT);
        if (products.existsBySku(sku)) {
            throw new ConflictException("SKU màu mới đã tồn tại.");
        }
        List<String> photos = gallery(request.imageUrls());
        ProductVersion version;
        if (existing.isEmpty()) {
            if (request.versionId() != null) {
                throw new BadRequestException("Hãy khởi tạo phiên bản gốc trước khi thêm màu.");
            }
            if (request.defaultColorName() == null || request.defaultColorName().isBlank()) {
                throw new BadRequestException("Hãy nhập màu của SKU hiện tại trước khi thêm màu mới.");
            }
            String originalColor = request.defaultColorName().strip();
            if (originalColor.equalsIgnoreCase(color)) {
                throw new ConflictException("Màu mới phải khác màu của SKU hiện tại.");
            }
            // Contract V11 tiếp tục dùng được; ADMIN có thể đổi nhãn trung tính qua API phiên bản.
            version = versions.save(new ProductVersion(parent, "Phiên bản hiện tại"));
            parent.addVersion(version);
            ProductVariant original = variants.save(new ProductVariant(
                    parent, parent, version, originalColor, request.defaultColorHex()));
            parent.addVariant(original);
        } else {
            Long versionId = request.versionId() == null
                    ? existing.stream().filter(value -> value.getSkuProduct().getId().equals(productId))
                            .findFirst().orElseThrow().getVersion().getId()
                    : request.versionId();
            version = parent.getVersions().stream().filter(value -> value.getId().equals(versionId))
                    .findFirst().orElseThrow(() -> new ResourceNotFoundException("Phiên bản không thuộc sản phẩm này."));
        }
        if (version.isArchived()) {
            throw new ConflictException("Hãy khôi phục phiên bản trước khi thêm màu.");
        }
        if (parent.getVariants().stream().filter(value -> value.getVersion().getId().equals(version.getId()))
                .count() >= 30) {
            throw new BadRequestException("Một phiên bản chỉ được khai báo tối đa 30 màu.");
        }
        if (variants.existsByVersionIdAndColorKey(version.getId(), color.toLowerCase(Locale.ROOT))) {
            throw new ConflictException("Phiên bản đã có màu này.");
        }

        Product child = new Product(
                parent.getCategory(), sku, ProductService.variantSkuName(parent.getName(), version.getName(), color),
                request.unitPrice() == null ? parent.getUnitPrice() : request.unitPrice(),
                ProductStatus.ACTIVE, image(request.imageUrl()), parent.getDescription());
        child.updateBrand(parent.getBrand());
        child.replaceImageUrls(photos);
        child.replaceSpecifications(version.getEffectiveSpecifications());
        try {
            // UNIQUE SKU xử lý cả hai ADMIN tạo trùng mã ở hai nhóm khác nhau cùng thời điểm.
            products.saveAndFlush(child);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("SKU màu mới đã tồn tại; hãy chọn mã khác.");
        }
        ProductVariant variant = variants.save(new ProductVariant(parent, child, version, color, request.colorHex()));
        parent.addVariant(variant);
        return ProductResponse.from(parent);
    }

    /** Sửa giá/ảnh hoặc dừng bán riêng một màu; màu gốc có thể dừng mà trang chung vẫn hoạt động. */
    @Transactional
    public ProductResponse update(Long productId, Long variantId, UpdateProductVariantRequest request) {
        Product parent = lockRoot(productId);
        ProductVariant variant = parent.getVariants().stream()
                .filter(value -> value.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Màu không thuộc sản phẩm này."));
        Product sku = variant.getSkuProduct();
        if (request.colorName() != null || request.colorHex() != null) {
            String name = request.colorName() == null ? variant.getColorName() : request.colorName().strip();
            if (name.isBlank() || name.length() > 80) throw new BadRequestException("Tên màu phải có từ 1 đến 80 ký tự.");
            String key = name.toLowerCase(Locale.ROOT);
            boolean duplicate = parent.getVariants().stream().anyMatch(value ->
                    !value.getId().equals(variantId) && value.getVersion().getId().equals(variant.getVersion().getId())
                            && value.getColorKey().equals(key));
            if (duplicate) throw new ConflictException("Tên màu đã tồn tại trong phiên bản này, kể cả màu đã xóa. Hãy dùng tên khác hoặc khôi phục màu cũ.");
            variant.updateColor(name, request.colorHex() == null ? variant.getColorHex() : request.colorHex().toUpperCase(Locale.ROOT));
        }
        if (request.unitPrice() != null) sku.update(null, request.unitPrice(), null);
        if (request.status() != null) {
            if (request.status() == ProductStatus.ACTIVE && variant.getVersion().isArchived()) {
                throw new ConflictException("Hãy khôi phục phiên bản trước khi mở bán hoặc khôi phục màu.");
            }
            variant.setEnabled(request.status() == ProductStatus.ACTIVE);
            if (!sku.getId().equals(productId)) sku.update(null, null, request.status());
        }
        if (request.imageUrl() != null) sku.updateImageUrl(image(request.imageUrl()));
        if (request.imageUrls() != null) sku.replaceImageUrls(gallery(request.imageUrls()));
        return ProductResponse.from(parent);
    }

    /** Xóa một màu khỏi lựa chọn mua; giữ SKU, ảnh, tồn và đơn cũ, kể cả màu của SKU gốc. */
    @Transactional
    public ProductResponse archive(Long productId, Long variantId) {
        Product parent = lockRoot(productId);
        ProductVariant variant = parent.getVariants().stream()
                .filter(value -> value.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Màu không thuộc sản phẩm này."));
        variant.archive();
        if (!variant.getSkuProduct().getId().equals(productId)) {
            variant.getSkuProduct().update(null, null, ProductStatus.INACTIVE);
        }
        return ProductResponse.from(parent);
    }

    /** Cấm nhóm lồng nhau; mọi thay đổi màu dùng cùng khóa gốc để tránh tạo màu trùng đồng thời. */
    Product lockRoot(Long productId) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        if (variants.findBySkuProductId(productId)
                .filter(value -> !value.getProduct().getId().equals(productId)).isPresent()) {
            throw new BadRequestException("Hãy thêm hoặc sửa màu tại sản phẩm gốc.");
        }
        return product;
    }

    /** Chuỗi rỗng xóa ảnh; không lấy ảnh màu khác để giả làm ảnh của màu mới. */
    private String image(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Giữ thứ tự và chặn link ảnh trùng; lỗi sẽ rollback cả việc cấu hình màu gốc nếu có. */
    private List<String> gallery(List<String> values) {
        if (values == null) return List.of();
        List<String> result = values.stream().map(String::strip).toList();
        if (result.stream().distinct().count() != result.size()) {
            throw new BadRequestException("Các link ảnh bổ sung không được trùng nhau.");
        }
        return result;
    }
}
