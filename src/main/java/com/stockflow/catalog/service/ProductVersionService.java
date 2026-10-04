package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.domain.ProductVariant;
import com.stockflow.catalog.domain.ProductVersion;
import com.stockflow.catalog.dto.CreateProductVersionRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductVersionRequest;
import com.stockflow.catalog.repository.ProductVariantRepository;
import com.stockflow.catalog.repository.ProductVersionRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN quản lý cấu hình của model dưới cùng khóa với màu và nội dung catalog.
 * Khởi tạo phiên bản gốc giữ nguyên SKU hiện tại; phiên bản mới chưa có màu không tự sinh hàng.
 */
@Service
public class ProductVersionService {

    private final ProductVariantService variantService;
    private final ProductVersionRepository versions;
    private final ProductVariantRepository variants;

    /** Dùng cùng khóa gốc để hai ADMIN không tạo trùng phiên bản hoặc trộn thông số. */
    public ProductVersionService(
            ProductVariantService variantService,
            ProductVersionRepository versions,
            ProductVariantRepository variants) {
        this.variantService = variantService;
        this.versions = versions;
        this.variants = variants;
    }

    /** Khai báo phiên bản; lần đầu gắn SKU gốc vào màu người dùng nhập, không chuyển tồn kho. */
    @Transactional
    public ProductResponse create(Long productId, CreateProductVersionRequest request) {
        Product product = variantService.lockRoot(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ConflictException("Hãy kích hoạt sản phẩm trước khi thêm phiên bản.");
        }
        if (product.getVersions().size() >= 20) {
            throw new BadRequestException("Một model chỉ được khai báo tối đa 20 phiên bản.");
        }
        checkName(productId, request.name(), null);
        boolean first = product.getVersions().isEmpty();
        if (first && (request.defaultColorName() == null || request.defaultColorName().isBlank())) {
            throw new BadRequestException("Hãy nhập màu của SKU hiện tại để khởi tạo phiên bản đầu tiên.");
        }
        ProductVersion version = new ProductVersion(product, request.name());
        version.replaceSpecifications(ProductService.normalizeSpecifications(request.specifications()));
        validateSpecifications(version);
        versions.save(version);
        product.addVersion(version);
        if (first) {
            ProductVariant original = variants.save(new ProductVariant(
                    product, product, version, request.defaultColorName(), request.defaultColorHex()));
            product.addVariant(original);
        }
        return ProductResponse.from(product);
    }

    /** Sửa tên/thông số nhưng giữ ID SKU và giá/ảnh/tồn riêng của tất cả màu trong phiên bản. */
    @Transactional
    public ProductResponse update(Long productId, Long versionId, UpdateProductVersionRequest request) {
        Product product = variantService.lockRoot(productId);
        ProductVersion version = product.getVersions().stream()
                .filter(value -> value.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Phiên bản không thuộc sản phẩm này."));
        if (request.name() != null) {
            checkName(productId, request.name(), version);
            version.rename(request.name());
        }
        if (request.specifications() != null) {
            version.replaceSpecifications(ProductService.normalizeSpecifications(request.specifications()));
        }
        validateSpecifications(version);
        for (ProductVariant variant : product.getVariants()) {
            Product sku = variant.getSkuProduct();
            if (!variant.getVersion().getId().equals(versionId) || sku.getId().equals(productId)) {
                continue;
            }
            sku.update(ProductService.variantSkuName(product.getName(), version.getName(), variant.getColorName()),
                    null, null);
            sku.replaceSpecifications(version.getEffectiveSpecifications());
        }
        return ProductResponse.from(product);
    }

    /** ADMIN xóa phiên bản khỏi cửa hàng trong cùng khóa model; không xóa hay xuất/hoàn kho SKU. */
    @Transactional
    public ProductResponse archive(Long productId, Long versionId) {
        Product product = variantService.lockRoot(productId);
        requireVersion(product, versionId).setArchived(true);
        return ProductResponse.from(product);
    }

    /** Khôi phục giữ trạng thái từng màu trước đó; màu đã xóa riêng vẫn phải được khôi phục riêng. */
    @Transactional
    public ProductResponse restore(Long productId, Long versionId) {
        Product product = variantService.lockRoot(productId);
        requireVersion(product, versionId).setArchived(false);
        return ProductResponse.from(product);
    }

    /** Luôn xác minh cấu hình thuộc model đang khóa để không thao tác nhầm sản phẩm khác. */
    private ProductVersion requireVersion(Product product, Long versionId) {
        return product.getVersions().stream()
                .filter(value -> value.getId().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Phiên bản không thuộc sản phẩm này."));
    }

    /** Tên chuẩn hóa chỉ duy nhất trong model; đổi tên của chính phiên bản vẫn hợp lệ. */
    private void checkName(Long productId, String name, ProductVersion current) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        if ((current == null || !current.getNameKey().equals(key))
                && versions.existsByProductIdAndNameKey(productId, key)) {
            throw new ConflictException("Sản phẩm đã có phiên bản này.");
        }
    }

    /** Bảng hiệu lực vẫn tuân thủ giới hạn 60 dòng của SKU và giao diện hiện có. */
    static void validateSpecifications(ProductVersion version) {
        if (version.getEffectiveSpecifications().size() > 60) {
            throw new BadRequestException("Tổng thông số chung và riêng của phiên bản không được vượt 60 dòng.");
        }
    }
}
