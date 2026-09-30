-- Quản lý tồn kho theo từng cặp sản phẩm và kho, không cho phép số lượng âm.
-- Phân công nhân viên theo kho để ngăn truy cập kho ngoài phạm vi phụ trách.
CREATE TABLE warehouse_staff_assignments (
 user_id BIGINT NOT NULL REFERENCES users(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 PRIMARY KEY (user_id, warehouse_id)
);
CREATE TABLE inventories (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES products(id),
 warehouse_id BIGINT NOT NULL REFERENCES warehouses(id),
 available_quantity INTEGER NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
 reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
 version BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT uq_product_warehouse UNIQUE (product_id, warehouse_id)
);
-- Sổ cái lưu người thực hiện và số tồn vật lý trước/sau mỗi biến động.
CREATE TABLE inventory_movements (
 id BIGSERIAL PRIMARY KEY,
 inventory_id BIGINT NOT NULL REFERENCES inventories(id),
 performed_by BIGINT NOT NULL REFERENCES users(id),
 type VARCHAR(50) NOT NULL CHECK (type IN ('GOODS_RECEIPT','RESERVATION_HOLD','RESERVATION_RELEASE','DISPATCH','RETURN_RESTOCK','STOCK_ADJUSTMENT')),
 quantity INTEGER NOT NULL CHECK (quantity > 0),
 balance_before INTEGER NOT NULL CHECK (balance_before >= 0),
 balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
 reference_type VARCHAR(50),
 reference_id BIGINT,
 note VARCHAR(255),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Hỗ trợ tra cứu lịch sử mới nhất của một dòng tồn kho.
CREATE INDEX idx_movements_inventory_created ON inventory_movements(inventory_id, created_at DESC);
-- Chặn cả UPDATE và DELETE ở database để giữ nguyên lịch sử kiểm toán.
CREATE FUNCTION prevent_inventory_movement_change() RETURNS trigger AS $$
BEGIN
 RAISE EXCEPTION 'Lịch sử biến động kho là bất biến, không được sửa hoặc xóa.';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER immutable_inventory_movements BEFORE UPDATE OR DELETE ON inventory_movements
FOR EACH ROW EXECUTE FUNCTION prevent_inventory_movement_change();
