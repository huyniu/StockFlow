-- A stable warehouse code may be configured before demo warehouses are created.
-- Resolve it to an ACTIVE warehouse ID at read time; never create/reassign operational data.
ALTER TABLE storefront_map_locations ADD COLUMN warehouse_code VARCHAR(80);

UPDATE storefront_map_locations SET warehouse_code = 'WH-HAN-01' WHERE code = 'DEMO-HAN';
UPDATE storefront_map_locations SET warehouse_code = 'WH-DAD-01' WHERE code = 'DEMO-DAD';
UPDATE storefront_map_locations SET warehouse_code = 'WH-SGN-01' WHERE code = 'DEMO-SGN';
