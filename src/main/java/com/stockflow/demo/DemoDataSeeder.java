package com.stockflow.demo;

import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dữ liệu StockFlow Tech chỉ được thêm khi chưa tồn tại, nhận diện theo email/slug/SKU/code.
 * Cửa hàng tập trung phụ kiện máy tính; dữ liệu cũ không bị đổi tên, xóa hay chuyển trạng thái ngầm.
 * Nhập tồn kho qua service nghiệp vụ để GOODS_RECEIPT luôn có actor và snapshot balance hợp lệ.
 */
@Service
@Profile("demo")
public class DemoDataSeeder {

    // Ảnh Unsplash minh họa fixture công nghệ; chủ cửa hàng có thể thay URL qua form ADMIN.
    private static final String KEYBOARD_IMAGE =
            "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop&q=80";
    private static final String MOUSE_IMAGE =
            "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&auto=format&fit=crop&q=80";
    private static final String HEADPHONE_IMAGE =
            "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80";

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
        User admin = account("admin@stockflow.com", "Admin@123", "Quản trị viên demo", "ADMIN");
        account("manager@stockflow.com", "Manager@123", "Quản lý demo", "MANAGER");
        User staff = account("staff.hn@stockflow.com", "Staff@123", "Nhân viên kho Hà Nội", "WAREHOUSE_STAFF");
        account("customer@stockflow.com", "Customer@123", "Khách hàng demo", "CUSTOMER");

        List<Warehouse> demoWarehouses = List.of(
                warehouse("WH-HAN-01", "Kho Tổng Hà Nội", "Long Biên, Hà Nội"),
                warehouse("WH-DAD-01", "Kho Đà Nẵng", "Hải Châu, Đà Nẵng"),
                warehouse("WH-SGN-01", "Kho TP.HCM", "Thành phố Thủ Đức, TP.HCM"));
        assign(staff.getId(), demoWarehouses.get(0).getId());

        if (!seedCatalog) {
            // Danh mục tham khảo đã có qua Flyway; giữ mọi quyết định thêm/sửa/xóa catalog của chủ cửa hàng.
            return;
        }

        Map<String, Category> demoCategories = new LinkedHashMap<>();
        demoCategories.put("ban-phim-chuot", category("Bàn phím & Chuột", "ban-phim-chuot"));
        demoCategories.put("tai-nghe-loa", category("Tai nghe & Loa", "tai-nghe-loa"));
        demoCategories.put("webcam-micro", category("Webcam & Micro", "webcam-micro"));
        demoCategories.put("hub-cap-sac", category("Hub, Cáp & Bộ sạc", "hub-cap-sac"));
        demoCategories.put("man-hinh-ban-lam-viec",
                category("Màn hình & Phụ kiện bàn làm việc", "man-hinh-ban-lam-viec"));

        // Mỗi cấu hình bán là một SKU riêng; giá và ảnh dưới đây chỉ là dữ liệu trình diễn.
        List<ProductSeed> catalog = List.of(
                new ProductSeed("ban-phim-chuot", "TECH-KBD-01",
                        "Bàn phím cơ 75%", "990000", KEYBOARD_IMAGE),
                new ProductSeed("ban-phim-chuot", "TECH-KBD-02",
                        "Bàn phím cơ full-size", "1290000", KEYBOARD_IMAGE),
                new ProductSeed("ban-phim-chuot", "TECH-KBD-03",
                        "Bàn phím không dây văn phòng", "590000", KEYBOARD_IMAGE),
                new ProductSeed("ban-phim-chuot", "TECH-MOUSE-01",
                        "Chuột không dây công thái học", "790000", MOUSE_IMAGE),
                new ProductSeed("ban-phim-chuot", "TECH-MOUSE-02",
                        "Chuột gaming có dây", "490000", MOUSE_IMAGE),
                new ProductSeed("ban-phim-chuot", "TECH-MOUSE-03",
                        "Chuột Bluetooth nhỏ gọn", "290000", MOUSE_IMAGE),
                new ProductSeed("tai-nghe-loa", "TECH-AUDIO-01",
                        "Tai nghe chụp tai không dây", "1290000", HEADPHONE_IMAGE),
                new ProductSeed("tai-nghe-loa", "TECH-AUDIO-02",
                        "Tai nghe gaming có micro", "790000", HEADPHONE_IMAGE),
                new ProductSeed("tai-nghe-loa", "TECH-AUDIO-03",
                        "Tai nghe chụp tai có dây", "490000", HEADPHONE_IMAGE),
                new ProductSeed("tai-nghe-loa", "TECH-AUDIO-04",
                        "Tai nghe kiểm âm", "1590000", HEADPHONE_IMAGE),
                new ProductSeed("tai-nghe-loa", "TECH-SPEAKER-01",
                        "Loa vi tính 2.0", "890000", null),
                new ProductSeed("webcam-micro", "TECH-CAM-01",
                        "Webcam Full HD", "690000", null),
                new ProductSeed("webcam-micro", "TECH-CAM-02",
                        "Webcam 2K", "1190000", null),
                new ProductSeed("webcam-micro", "TECH-MIC-01",
                        "Micro USB để bàn", "990000", null),
                new ProductSeed("webcam-micro", "TECH-MIC-02",
                        "Micro USB kèm chân đỡ", "1490000", null),
                new ProductSeed("hub-cap-sac", "TECH-HUB-01",
                        "Hub USB-C 6 trong 1", "790000", null),
                new ProductSeed("hub-cap-sac", "TECH-HUB-02",
                        "Hub USB 3.0 bốn cổng", "290000", null),
                new ProductSeed("hub-cap-sac", "TECH-CABLE-01",
                        "Cáp USB-C 100W một mét", "149000", null),
                new ProductSeed("hub-cap-sac", "TECH-CABLE-02",
                        "Cáp HDMI hai mét", "129000", null),
                new ProductSeed("hub-cap-sac", "TECH-CHARGER-01",
                        "Bộ sạc GaN 65W", "690000", null),
                new ProductSeed("man-hinh-ban-lam-viec", "TECH-MONITOR-01",
                        "Màn hình IPS 24 inch", "2990000", null),
                new ProductSeed("man-hinh-ban-lam-viec", "TECH-MONITOR-02",
                        "Màn hình 27 inch QHD", "4990000", null),
                new ProductSeed("man-hinh-ban-lam-viec", "TECH-DESK-01",
                        "Giá đỡ laptop nhôm", "390000", null),
                new ProductSeed("man-hinh-ban-lam-viec", "TECH-DESK-02",
                        "Tay đỡ màn hình đơn", "790000", null));

        for (int index = 0; index < catalog.size(); index++) {
            ProductSeed item = catalog.get(index);
            Product product = products.findBySku(item.sku()).orElseGet(() -> products.save(new Product(
                    demoCategories.get(item.categorySlug()),
                    item.sku(),
                    item.name(),
                    new BigDecimal(item.price()),
                    ProductStatus.ACTIVE,
                    item.imageUrl())));
        }
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
                        quantity, "Nhập tồn ban đầu cho StockFlow Tech demo"), admin.getId());
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
        user.setEmailVerified(true);
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

    /** Fixture công nghệ có ảnh tùy chọn; bản ghi đã tồn tại giữ nguyên thông tin do ADMIN chỉnh sửa. */
    private record ProductSeed(
            String categorySlug,
            String sku,
            String name,
            String price,
            String imageUrl) {
    }
}
