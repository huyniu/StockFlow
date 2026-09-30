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
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dữ liệu portfolio chỉ được thêm khi chưa tồn tại, nhận diện theo email/slug/SKU/code.
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

    /** Nhận repository và nghiệp vụ nhập kho, tránh viết trực tiếp tồn kho mà thiếu movement. */
    public DemoDataSeeder(UserRepository users, RoleRepository roles, CategoryRepository categories,
            ProductRepository products, WarehouseRepository warehouses, InventoryRepository inventories,
            InventoryService inventoryService, PasswordEncoder passwords, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.roles = roles;
        this.categories = categories;
        this.products = products;
        this.warehouses = warehouses;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    /** Nạp 4 tài khoản, 3 kho, 4 danh mục, 24 sản phẩm và tồn kho mới trong cùng transaction. */
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

        Map<String, Category> demoCategories = new LinkedHashMap<>();
        demoCategories.put("dien-tu", category("Điện tử", "dien-tu"));
        demoCategories.put("gia-dung", category("Gia dụng", "gia-dung"));
        demoCategories.put("thoi-trang", category("Thời trang", "thoi-trang"));
        demoCategories.put("phu-kien", category("Phụ kiện", "phu-kien"));

        List<ProductSeed> catalog = List.of(
                new ProductSeed("dien-tu", "ELE-PHONE-01", "Điện thoại Android 128GB", "5990000"),
                new ProductSeed("dien-tu", "ELE-LAPTOP-01", "Laptop văn phòng 14 inch", "15990000"),
                new ProductSeed("dien-tu", "ELE-HEADSET-01", "Tai nghe Bluetooth", "790000"),
                new ProductSeed("dien-tu", "ELE-TABLET-01", "Máy tính bảng 10 inch", "4990000"),
                new ProductSeed("dien-tu", "ELE-TV-01", "Smart TV 43 inch", "7490000"),
                new ProductSeed("dien-tu", "ELE-MONITOR-01", "Màn hình IPS 24 inch", "2990000"),
                new ProductSeed("gia-dung", "HOME-RICE-01", "Nồi cơm điện 1.8 lít", "890000"),
                new ProductSeed("gia-dung", "HOME-KETTLE-01", "Ấm siêu tốc inox", "350000"),
                new ProductSeed("gia-dung", "HOME-STOVE-01", "Bếp từ đơn", "1290000"),
                new ProductSeed("gia-dung", "HOME-VACUUM-01", "Máy hút bụi gia đình", "2190000"),
                new ProductSeed("gia-dung", "HOME-AIR-01", "Máy lọc không khí", "3490000"),
                new ProductSeed("gia-dung", "HOME-IRON-01", "Bàn ủi hơi nước", "590000"),
                new ProductSeed("thoi-trang", "FASH-SHIRT-01", "Áo sơ mi cotton", "299000"),
                new ProductSeed("thoi-trang", "FASH-JACKET-01", "Áo khoác chống nắng", "399000"),
                new ProductSeed("thoi-trang", "FASH-JEANS-01", "Quần jean nam", "549000"),
                new ProductSeed("thoi-trang", "FASH-DRESS-01", "Váy công sở", "649000"),
                new ProductSeed("thoi-trang", "FASH-SHOES-01", "Giày thể thao", "899000"),
                new ProductSeed("thoi-trang", "FASH-BAG-01", "Ba lô đi làm", "459000"),
                new ProductSeed("phu-kien", "ACC-CHARGER-01", "Sạc nhanh USB-C 30W", "290000"),
                new ProductSeed("phu-kien", "ACC-POWER-01", "Pin dự phòng 10000mAh", "490000"),
                new ProductSeed("phu-kien", "ACC-MOUSE-01", "Chuột không dây", "249000"),
                new ProductSeed("phu-kien", "ACC-KEYBOARD-01", "Bàn phím cơ", "990000"),
                new ProductSeed("phu-kien", "ACC-CABLE-01", "Cáp USB-C 1 mét", "99000"),
                new ProductSeed("phu-kien", "ACC-CASE-01", "Ốp lưng điện thoại", "149000"));

        for (int index = 0; index < catalog.size(); index++) {
            ProductSeed item = catalog.get(index);
            Product product = products.findBySku(item.sku()).orElseGet(() -> products.save(new Product(
                    demoCategories.get(item.categorySlug()), item.sku(), item.name(),
                    new BigDecimal(item.price()), ProductStatus.ACTIVE)));
            for (Warehouse warehouse : demoWarehouses) {
                // Inventory đã tồn tại không được bơm lại tồn đầu kỳ, kể cả sau khi bán hết.
                if (inventories.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).isEmpty()) {
                    int quantity = index % 8 == 0 ? 6 : 40 + index * 2;
                    inventoryService.stockIn(new StockInRequest(product.getId(), warehouse.getId(),
                            quantity, "Nhập tồn ban đầu cho portfolio demo"), admin.getId());
                }
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
        return user;
    }

    /** Kho hiện có giữ nguyên tên, địa chỉ và trạng thái. */
    private Warehouse warehouse(String code, String name, String address) {
        return warehouses.findByCode(code).orElseGet(() ->
                warehouses.save(new Warehouse(code, name, address, WarehouseStatus.ACTIVE)));
    }

    /** Danh mục hiện có được nhận diện bằng slug ổn định. */
    private Category category(String name, String slug) {
        return categories.findBySlug(slug).orElseGet(() -> categories.save(new Category(name, slug)));
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

    /** Snapshot catalog cố định dành cho dữ liệu mẫu. */
    private record ProductSeed(String categorySlug, String sku, String name, String price) {
    }
}
