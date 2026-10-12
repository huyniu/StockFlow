package com.stockflow.demo;

import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.user.domain.User;
import com.stockflow.user.domain.UserStatus;
import com.stockflow.auth.security.DemoAccountPolicy;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dữ liệu StockFlow Tech chỉ được thêm khi chưa tồn tại, nhận diện theo email/slug/SKU/code.
 * Catalog mẫu chỉ nạp khi được bật; không ghi đè sản phẩm hay mật khẩu hiện có.
 * Nhập tồn kho qua service nghiệp vụ để GOODS_RECEIPT luôn có actor và snapshot balance hợp lệ.
 */
@Service
@Profile("demo")
public class DemoDataSeeder {

    private final UserRepository users;
    private final RoleRepository roles;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventories;
    private final InventoryService inventoryService;
    private final PasswordEncoder passwords;
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean seedCatalog;

    /** Nhận repository và nghiệp vụ nhập kho, tránh viết trực tiếp tồn kho mà thiếu movement. */
    public DemoDataSeeder(UserRepository users, RoleRepository roles, CategoryRepository categories,
            ProductRepository products, WarehouseRepository warehouses, InventoryRepository inventories,
            InventoryService inventoryService, PasswordEncoder passwords, NamedParameterJdbcTemplate jdbc,
            @Value("${app.demo.seed-catalog:false}") boolean seedCatalog) {
        this.users = users;
        this.roles = roles;
        this.categories = categories;
        this.products = products;
        this.warehouses = warehouses;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.seedCatalog = seedCatalog;
    }

    /**
     * Chuẩn bị tài khoản/kho; chỉ thêm danh mục/sản phẩm/tồn mẫu khi bật seed-catalog.
     * Chế độ nhập tay không khôi phục danh mục đã xóa hoặc tạo lại slug cũ sau khi ADMIN sửa.
     */
    @Transactional
    public void seed() {
        User admin = account("admin@stockflow.com", UUID.randomUUID().toString(), "Quản trị viên demo", "ADMIN");
        account("manager@stockflow.com", UUID.randomUUID().toString(), "Quản lý demo", "MANAGER");
        User staff = account("staff.hn@stockflow.com", UUID.randomUUID().toString(), "Nhân viên kho Hà Nội", "WAREHOUSE_STAFF");
        account("customer@stockflow.com", "Customer@123", "Khách hàng demo", "CUSTOMER");

        List<Warehouse> demoWarehouses = List.of(
                warehouse("WH-HAN-01", "Kho Tổng Hà Nội", "Long Biên, Hà Nội"),
                warehouse("WH-DAD-01", "Kho Đà Nẵng", "Hải Châu, Đà Nẵng"),
                warehouse("WH-SGN-01", "Kho TP.HCM", "Thành phố Thủ Đức, TP.HCM"));
        if (staff.getOperatorRecoveredAt() == null) assign(staff.getId(), demoWarehouses.get(0).getId());

        if (!seedCatalog) {
            // Danh mục tham khảo đã có qua Flyway; giữ mọi quyết định thêm/sửa/xóa catalog của chủ cửa hàng.
            return;
        }

        seedProducts(DemoCatalog.load());
        seedStock(admin.getId(), demoWarehouses);
    }

    private void seedProducts(DemoCatalog catalog) {
        Map<String, Category> demoCategories = new LinkedHashMap<>();
        for (var item : catalog.categories()) {
            demoCategories.put(item.slug(), category(item.name(), item.slug()));
        }
        for (var item : catalog.products()) {
            if (products.findBySku(item.sku()).isEmpty()) {
                products.save(new Product(demoCategories.get(item.categorySlug()), item.sku(),
                        item.name(), item.price(), ProductStatus.ACTIVE, item.imageUrl()));
            }
        }
    }

    private void seedStock(Long adminId, List<Warehouse> demoWarehouses) {
        // Nạp cả sản phẩm hiện có của cửa hàng, không chỉ 24 SKU TECH mẫu.
        for (Product product : products.findAll()) {
            if (product.getStatus() != ProductStatus.ACTIVE) continue;
            for (Warehouse warehouse : demoWarehouses) {
                if (warehouse.getStatus() != WarehouseStatus.ACTIVE) continue;
                var existing = inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId());
                if (existing.isPresent()) {
                    var inventory = existing.get();
                    if (inventory.getPhysicalQuantity() != 0) continue;
                    Long movements = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM inventory_movements WHERE inventory_id = :id",
                            new MapSqlParameterSource("id", inventory.getId()), Long.class);
                    // Tồn 0 có lịch sử là hàng đã vận hành/bán hết, không phải tồn đầu kỳ chưa nạp.
                    if (movements != null && movements > 0) continue;
                }
                int quantity = 20 + Math.floorMod(Objects.hash(product.getSku(), warehouse.getCode()), 31);
                inventoryService.stockIn(new StockInRequest(product.getId(), warehouse.getId(),
                        quantity, "Nhập tồn ban đầu cho StockFlow Tech demo"), adminId);
            }
        }
    }

    /** Mật khẩu chỉ được BCrypt khi tạo mới; không reset thông tin tài khoản hiện có. */
    private User account(String email, String password, String name, String role) {
        User user = users.findByEmail(email).orElseGet(() -> users.save(new User(
                email, passwords.encode(password), name,
                roles.findByName(role).orElseThrow(() -> new IllegalStateException("Thiếu role " + role)))));
        if (!user.getRole().getName().equals(role)) {
            throw new IllegalStateException("Email demo đã tồn tại với role khác: " + email);
        }
        if (user.getOperatorRecoveredAt() != null) return user;
        user.setEmailVerified(true);
        if (DemoAccountPolicy.isPublicOperator(user)) {
            if (user.getStatus() == UserStatus.ACTIVE) user.invalidateAccessTokens();
            user.setStatus(UserStatus.INACTIVE);
        }
        return user;
    }

    /** Kho hiện có giữ nguyên tên, địa chỉ và trạng thái. */
    private Warehouse warehouse(String code, String name, String address) {
        return warehouses.findByCode(code).orElseGet(() ->
                warehouses.save(new Warehouse(code, name, address, WarehouseStatus.ACTIVE)));
    }

    /** Giữ nhóm đã có; nhóm Tai nghe & Loa mới nằm dưới Âm thanh như dữ liệu nâng cấp V10. */
    private Category category(String name, String slug) {
        return categories.findBySlug(slug).orElseGet(() -> {
            Category parent = "tai-nghe-loa".equals(slug)
                    ? categories.findBySlug("am-thanh-mic-thu-am").orElse(null) : null;
            return categories.save(new Category(name, slug, parent));
        });
    }

    /** Phân công nhân viên Hà Nội một lần để endpoint nhập kho và tra cứu hoạt động đúng quyền. */
    private void assign(Long userId, Long warehouseId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId).addValue("warehouseId", warehouseId);
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM warehouse_staff_assignments
                WHERE user_id = :userId
                  AND warehouse_id = :warehouseId
                """, parameters, Long.class);
        if (count == 0) {
            jdbc.update("""
                    INSERT INTO warehouse_staff_assignments (user_id, warehouse_id)
                    VALUES (:userId, :warehouseId)
                    """, parameters);
        }
    }

}
