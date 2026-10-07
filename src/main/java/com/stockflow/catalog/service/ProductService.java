package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.domain.ProductStatus;
import com.stockflow.catalog.domain.ProductSpecification;
import com.stockflow.catalog.domain.ProductVariant;
import com.stockflow.catalog.dto.ProductSpecificationDto;
import com.stockflow.catalog.dto.CreateProductRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductRequest;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.BrandRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.ProductVariantRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý sản phẩm: tạo, cập nhật, xem chi tiết và tìm kiếm có phân trang.
 */
@Service
public class ProductService {

    // NUMERIC(12,2) của giá sản phẩm cho phép tối đa 10 chữ số phần nguyên và 2 chữ số thập phân.
    private static final BigDecimal MAX_PRODUCT_PRICE = new BigDecimal("9999999999.99");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CategoryHierarchy categoryHierarchy;
    private final ProductVariantRepository variantRepository;

    /**
     * Inject repository sản phẩm và danh mục để xử lý các ràng buộc giữa product và category.
     */
    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            CategoryHierarchy categoryHierarchy,
            ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.categoryHierarchy = categoryHierarchy;
        this.variantRepository = variantRepository;
    }

    /**
     * Lọc và sắp xếp tại database; ID là thứ tự phụ để sản phẩm cùng giá không lặp hoặc mất giữa hai trang.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(
            Long categoryId,
            ProductStatus status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long brandId,
            boolean grouped,
            Pageable pageable) {
        return listProducts(categoryId, status, search, minPrice, maxPrice, brandId, grouped, pageable, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Long categoryId, ProductStatus status, String search,
            BigDecimal minPrice, BigDecimal maxPrice, Long brandId, boolean grouped, Pageable pageable,
            String specificationName, String specificationValue) {
        validatePriceRange(minPrice, maxPrice);
        if (brandId != null && brandId <= 0) {
            throw new BadRequestException("ID thương hiệu phải lớn hơn 0.");
        }
        List<Long> categoryIds = categoryId == null ? null : categoryHierarchy.descendantsOf(categoryId);
        Pageable stablePage = stableProductPage(pageable);
        Specification<Product> filter = buildFilter(
                categoryIds, status, search, minPrice, maxPrice, brandId, grouped);
        filter = filter.and(ProductAttributeFilter.create(specificationName, specificationValue, grouped));
        if (grouped) {
            // Sắp theo giá từ tại SQL, trước LIMIT; không lấy một trang rồi mới sắp lại bằng Java.
            filter = filter.and((root, query, builder) -> {
                if (!Long.class.equals(query.getResultType())) {
                    query.orderBy(stablePage.getSort().stream().map(order -> {
                        Expression<?> value = "unitPrice".equals(order.getProperty())
                                ? groupedPrice(root, query, builder) : root.get(order.getProperty());
                        return order.isAscending() ? builder.asc(value) : builder.desc(value);
                    }).toList());
                }
                return builder.conjunction();
            });
        }
        Pageable queryPage = grouped
                ? (stablePage.isPaged()
                        ? PageRequest.of(stablePage.getPageNumber(), stablePage.getPageSize())
                        : Pageable.unpaged())
                : stablePage;
        Page<Product> page = productRepository.findAll(filter, queryPage);
        Map<Long, Long> parents = page.isEmpty() ? Map.of() : variantRepository.findMappings(
                        page.getContent().stream().map(Product::getId).toList()).stream()
                .filter(variant -> !variant.getProduct().getId().equals(variant.getSkuProduct().getId()))
                .collect(Collectors.toMap(variant -> variant.getSkuProduct().getId(),
                        variant -> variant.getProduct().getId()));
        return page.map(product -> ProductResponse.from(product, parents.get(product.getId())));
    }

    /**
     * Lấy chi tiết một sản phẩm theo id, trả 404 nếu không tồn tại.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        Long parentId = variantRepository.findBySkuProductId(id)
                .filter(variant -> !variant.getProduct().getId().equals(id))
                .map(variant -> variant.getProduct().getId()).orElse(null);
        return ProductResponse.from(product, parentId);
    }

    /**
     * Tạo sản phẩm mới, chỉ được gọi bởi ADMIN ở controller. SKU phải duy nhất và category phải tồn tại.
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        List<String> imageUrls = normalizeImageUrls(request.imageUrls());
        String sku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU sản phẩm đã tồn tại.");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục sản phẩm."));

        Product product = new Product(
                category,
                sku,
                request.name().trim(),
                request.unitPrice(),
                request.status(),
                normalizeImageUrl(request.imageUrl()),
                normalizeDescription(request.description()));
        product.replaceImageUrls(imageUrls);
        product.replaceSpecifications(normalizeSpecifications(request.specifications()));
        if (request.brandId() != null) {
            product.updateBrand(brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại.")));
        }
        productRepository.save(product);
        return ProductResponse.from(product);
    }

    /**
     * PATCH trong một transaction có khóa sản phẩm; null giữ bộ ảnh, mảng rỗng xóa ảnh bổ sung.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        if (Boolean.TRUE.equals(request.clearBrand()) && request.brandId() != null) {
            throw new BadRequestException("Không được vừa chọn vừa xóa thương hiệu trong cùng yêu cầu.");
        }
        List<String> imageUrls = request.imageUrls() == null ? null : normalizeImageUrls(request.imageUrls());
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm."));
        if (variantRepository.findBySkuProductId(id)
                .filter(variant -> !variant.getProduct().getId().equals(id)).isPresent()) {
            throw new BadRequestException("Hãy sửa màu trong phần Biến thể của sản phẩm gốc.");
        }

        product.update(
                request.name() == null ? null : request.name().trim(),
                request.unitPrice(),
                request.status());
        if (request.imageUrl() != null) {
            product.updateImageUrl(normalizeImageUrl(request.imageUrl()));
        }
        if (request.description() != null) {
            product.updateDescription(normalizeDescription(request.description()));
        }
        if (imageUrls != null) {
            product.replaceImageUrls(imageUrls);
        }
        if (request.specifications() != null) {
            product.replaceSpecifications(normalizeSpecifications(request.specifications()));
        }
        // Dùng khóa sản phẩm hiện có để cập nhật hãng cùng nội dung/ảnh một cách nguyên tử.
        if (request.brandId() != null) {
            product.updateBrand(brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại.")));
        } else if (Boolean.TRUE.equals(request.clearBrand())) {
            product.updateBrand(null);
        }
        // Ghi đè riêng theo phiên bản được giữ lại khi ADMIN thay bảng thông số chung.
        product.getVersions().forEach(ProductVersionService::validateSpecifications);
        // Nội dung chung được đồng bộ; giá/ảnh và tồn kho riêng của SKU luôn được giữ lại.
        for (ProductVariant variant : product.getVariants()) {
            Product sku = variant.getSkuProduct();
            if (sku.getId().equals(id)) continue;
            sku.update(variantSkuName(product.getName(), variant.getVersion().getName(), variant.getColorName()),
                    null, null);
            sku.updateBrand(product.getBrand());
            sku.updateDescription(product.getDescription());
            sku.replaceSpecifications(variant.getVersion().getEffectiveSpecifications());
        }
        return ProductResponse.from(product);
    }

    /** Chuẩn hóa nhãn, giữ thứ tự và chặn hai dòng thông số cùng tên sau khi bỏ khoảng trắng. */
    static List<ProductSpecification> normalizeSpecifications(List<ProductSpecificationDto> specifications) {
        if (specifications == null) return List.of();
        List<ProductSpecification> values = specifications.stream()
                .map(value -> new ProductSpecification(value.name().strip(), value.value().strip())).toList();
        long distinct = values.stream().map(value -> value.getName().toLowerCase(Locale.ROOT)).distinct().count();
        if (distinct != values.size()) {
            throw new BadRequestException("Tên thông số không được trùng nhau.");
        }
        return values;
    }

    /** Tên SKU trong phiếu kho/đơn có màu; cắt phần tên chung để không vượt cột VARCHAR(200). */
    static String variantSkuName(String name, String version, String color) {
        String versionLabel = "Phiên bản hiện tại".equals(version)
                ? "" : " — " + version.substring(0, Math.min(version.length(), 100));
        String suffix = versionLabel + " — " + color;
        return name.substring(0, Math.min(name.length(), 200 - suffix.length())) + suffix;
    }

    /** Loại khoảng trắng bao quanh URL và thống nhất ảnh chưa có/xóa ảnh thành null trong database. */
    private String normalizeImageUrl(String imageUrl) {
        return imageUrl == null || imageUrl.isBlank() ? null : imageUrl.strip();
    }

    /** Giữ thứ tự ảnh, bỏ khoảng trắng và chặn URL trùng trước khi thay toàn bộ bộ ảnh. */
    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        List<String> normalized = imageUrls.stream().map(String::strip).toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new BadRequestException("Các link ảnh bổ sung không được trùng nhau.");
        }
        return normalized;
    }

    /** Bỏ khoảng trắng bao quanh nhưng giữ xuống dòng; không diễn giải nội dung thành HTML. */
    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.strip();
    }

    /**
     * Xây dựng Specification động để query chỉ thêm điều kiện khi client thật sự truyền filter.
     */
    private Specification<Product> buildFilter(
            List<Long> categoryIds,
            ProductStatus status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long brandId,
            boolean grouped) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (grouped) {
                // Loại SKU con tại SQL trước phân trang để mỗi model chỉ chiếm một thẻ sản phẩm.
                var child = query.subquery(Long.class);
                var variant = child.from(ProductVariant.class);
                child.select(variant.get("id")).where(
                        criteriaBuilder.equal(variant.get("skuProduct").get("id"), root.get("id")),
                        criteriaBuilder.notEqual(variant.get("product").get("id"), root.get("id")));
                predicates.add(criteriaBuilder.not(criteriaBuilder.exists(child)));
            }
            if (categoryIds != null) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (brandId != null) {
                predicates.add(criteriaBuilder.equal(root.get("brand").get("id"), brandId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            Expression<BigDecimal> price = grouped && (minPrice != null || maxPrice != null)
                    ? groupedPrice(root, query, criteriaBuilder) : root.get("unitPrice");
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(price, minPrice));
            }
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(price, maxPrice));
            }
            if (search != null && !search.isBlank()) {
                // Escape ký tự LIKE để phần trăm/gạch dưới trong tên hoặc SKU được tìm theo nghĩa đen.
                String term = search.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + term + "%";
                List<Predicate> terms = new ArrayList<>();
                terms.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), pattern, '\\')));
                if (grouped) {
                    // Tìm SKU/tên màu cũng trả trang chung, không làm xuất hiện thẻ SKU con.
                    var matchingColor = query.subquery(Long.class);
                    var color = matchingColor.from(ProductVariant.class);
                    matchingColor.select(color.get("id")).where(
                            criteriaBuilder.equal(color.get("product").get("id"), root.get("id")),
                            criteriaBuilder.or(
                                    criteriaBuilder.like(criteriaBuilder.lower(color.get("skuProduct").get("sku")), pattern, '\\'),
                                    criteriaBuilder.like(criteriaBuilder.lower(color.get("version").get("name")), pattern, '\\'),
                                    criteriaBuilder.like(criteriaBuilder.lower(color.get("colorName")), pattern, '\\')));
                    terms.add(criteriaBuilder.exists(matchingColor));
                }
                predicates.add(criteriaBuilder.or(terms.toArray(Predicate[]::new)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Giá từ chỉ lấy SKU còn bán của model ACTIVE; sản phẩm chưa có cấu hình giữ giá cũ. */
    private Expression<BigDecimal> groupedPrice(
            Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        var minimum = query.subquery(BigDecimal.class);
        var variant = minimum.from(ProductVariant.class);
        minimum.select(builder.min(variant.get("skuProduct").get("unitPrice"))).where(
                builder.equal(variant.get("product").get("id"), root.get("id")),
                builder.equal(root.get("status"), ProductStatus.ACTIVE),
                builder.isTrue(variant.get("enabled")),
                // Giá từ loại cấu hình đã xóa ngay trong SQL, trước lọc/sắp xếp/phân trang.
                builder.isFalse(variant.get("archived")),
                builder.isFalse(variant.get("version").get("archived")),
                builder.equal(variant.get("skuProduct").get("status"), ProductStatus.ACTIVE));
        return builder.coalesce(minimum, root.get("unitPrice"));
    }

    /** Khoảng giá gồm cả hai đầu mút; tham số sai phải trả 400, không âm thầm đổi khoảng người dùng chọn. */
    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        validatePriceBound(minPrice, "minPrice");
        validatePriceBound(maxPrice, "maxPrice");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Giá từ không được lớn hơn giá đến.");
        }
    }

    /** Kiểm tra tiền theo độ chính xác của schema; null nghĩa là không giới hạn đầu mút đó. */
    private void validatePriceBound(BigDecimal value, String parameter) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(MAX_PRODUCT_PRICE) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException(
                    "Tham số " + parameter + " phải từ 0 đến 9999999999.99 và có tối đa 2 chữ số thập phân.");
        }
    }

    /** Chỉ sắp xếp cột của sản phẩm; chặn đường dẫn collection làm nhân bản hàng hoặc phá phân trang. */
    private Pageable stableProductPage(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String property = switch (order.getProperty()) {
                case "unit_price" -> "unitPrice";
                case "created_at" -> "createdAt";
                case "id", "sku", "name", "unitPrice", "status", "createdAt" -> order.getProperty();
                default -> throw new BadRequestException("Trường sắp xếp sản phẩm không được hỗ trợ.");
            };
            orders.add(order.withProperty(property));
        }
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.asc("id"));
        }
        Sort sort = Sort.by(orders);
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : Pageable.unpaged(sort);
    }
}
