package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Brand;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.dto.BrandResponse;
import com.stockflow.catalog.dto.CreateBrandRequest;
import com.stockflow.catalog.dto.UpdateBrandLogoRequest;
import com.stockflow.catalog.repository.BrandRepository;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.common.exception.BadRequestException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ hãng: đọc công khai, ADMIN tạo/sửa logo; không thay phân loại/tồn kho của sản phẩm đã có. */
@Service
public class BrandService {

    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final CategoryHierarchy hierarchy;

    /** Nhận repository để tra cứu hãng và kiểm tra khóa ngoại danh mục trước khi ghi. */
    public BrandService(BrandRepository brands, CategoryRepository categories, CategoryHierarchy hierarchy) {
        this.brands = brands;
        this.categories = categories;
        this.hierarchy = hierarchy;
    }

    /** Hợp nhất danh mục gợi ý với danh mục thật đã sử dụng hãng; không chạy một query cho từng hãng. */
    @Transactional(readOnly = true)
    public List<BrandResponse> listBrands(Long categoryId) {
        if (categoryId != null && categoryId <= 0) {
            throw new BadRequestException("ID danh mục phải lớn hơn 0.");
        }
        Map<Long, Set<Long>> usedCategories = new HashMap<>();
        for (BrandRepository.ProductCategory mapping : brands.findProductCategories()) {
            usedCategories.computeIfAbsent(mapping.getBrandId(), id -> new HashSet<>())
                    .add(mapping.getCategoryId());
        }
        var tree = hierarchy.snapshot();
        return brands.findAllByOrderByIdAsc().stream()
                .map(brand -> response(brand, usedCategories.getOrDefault(brand.getId(), Set.of()), tree))
                .filter(brand -> categoryId == null || brand.categoryIds().contains(categoryId))
                .toList();
    }

    /** Tạo hãng mới và gợi ý menu cùng transaction; tên/slug trùng trả 409, danh mục không tồn tại trả 404. */
    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        if (brands.existsByNameIgnoreCase(name) || brands.existsBySlug(slug)) {
            throw new ConflictException("Tên hoặc slug thương hiệu đã tồn tại.");
        }
        List<Long> categoryIds = request.categoryIds() == null
                ? List.of()
                : request.categoryIds().stream().distinct().toList();
        List<Category> selected = categories.findAllById(categoryIds);
        if (selected.size() != categoryIds.size()) {
            throw new ResourceNotFoundException("Danh mục gợi ý không tồn tại.");
        }
        Brand brand = brands.save(new Brand(name, slug, new LinkedHashSet<>(selected), normalizeLogo(request.logoUrl())));
        return response(brand, Set.of(), hierarchy.snapshot());
    }

    /** Cập nhật logo cùng transaction; trả lại đầy đủ gợi ý hãng nhưng giữ nguyên mọi dữ liệu nghiệp vụ. */
    @Transactional
    public BrandResponse updateBrandLogo(Long id, UpdateBrandLogoRequest request) {
        if (id == null || id <= 0) {
            throw new BadRequestException("ID thương hiệu phải lớn hơn 0.");
        }
        Brand brand = brands.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại."));
        brand.updateLogo(normalizeLogo(request.logoUrl()));
        return response(brand, new HashSet<>(brands.findProductCategoryIds(id)), hierarchy.snapshot());
    }

    /** Chuỗi trống biểu thị chưa có/xóa logo; chỉ lưu URL đã được Bean Validation kiểm tra. */
    private String normalizeLogo(String logoUrl) {
        return logoUrl == null || logoUrl.isBlank() ? null : logoUrl.strip();
    }

    /** Sắp xếp/deduplicate ID để payload ổn định giữa các lần tải và không lộ JPA entity. */
    private BrandResponse response(Brand brand, Set<Long> usedCategories, CategoryHierarchy.Tree tree) {
        Set<Long> ids = new HashSet<>();
        usedCategories.forEach(id -> ids.addAll(tree.ancestors(id)));
        brand.getCategories().forEach(category -> {
            // Gợi ý cho cả nhánh; một hãng ở Tai nghe không bị gợi ý sang nhánh Loa cùng cấp.
            ids.addAll(tree.descendants(category.getId()));
            ids.addAll(tree.ancestors(category.getId()));
        });
        return new BrandResponse(
                brand.getId(), brand.getName(), brand.getSlug(), brand.getLogoUrl(), ids.stream().sorted().toList());
    }
}
