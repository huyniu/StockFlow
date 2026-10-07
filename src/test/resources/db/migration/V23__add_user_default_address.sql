ALTER TABLE users ADD COLUMN default_province_id INTEGER;
ALTER TABLE users ADD COLUMN default_province_name VARCHAR(150);
ALTER TABLE users ADD COLUMN default_district_id INTEGER;
ALTER TABLE users ADD COLUMN default_district_name VARCHAR(150);
ALTER TABLE users ADD COLUMN default_ward_code VARCHAR(20);
ALTER TABLE users ADD COLUMN default_ward_name VARCHAR(150);
ALTER TABLE users ADD COLUMN default_street_address VARCHAR(300);
