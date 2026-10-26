-- Public presentation locations are separate from operational warehouse/shipping addresses.
-- Demo points are created once; later edits and visibility settings survive restarts.
CREATE TABLE storefront_map_locations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    demo BOOLEAN NOT NULL DEFAULT TRUE,
    visible BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO storefront_map_locations (code, name, address, latitude, longitude) VALUES
('DEMO-HAN', 'StockFlow Hà Nội', 'Khu vực Long Biên, Hà Nội', 21.0456, 105.8919),
('DEMO-DAD', 'StockFlow Đà Nẵng', 'Khu vực Hải Châu, Đà Nẵng', 16.0471, 108.2068),
('DEMO-SGN', 'StockFlow TP.HCM', 'Khu vực Thủ Đức, TP.HCM', 10.8494, 106.7537);
