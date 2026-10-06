ALTER TABLE orders ADD COLUMN to_district_id INTEGER;
ALTER TABLE orders ADD COLUMN to_ward_code VARCHAR(20);
ALTER TABLE orders ADD COLUMN shipping_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD CONSTRAINT ck_orders_shipping_fee CHECK (shipping_fee >= 0);
