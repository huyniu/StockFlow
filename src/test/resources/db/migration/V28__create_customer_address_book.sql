CREATE TABLE customer_addresses (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 label VARCHAR(80) NOT NULL,
 recipient_name VARCHAR(150) NOT NULL,
 recipient_phone VARCHAR(30),
 province_id INTEGER NOT NULL,
 province_name VARCHAR(150) NOT NULL,
 district_id INTEGER NOT NULL,
 district_name VARCHAR(150) NOT NULL,
 ward_code VARCHAR(20) NOT NULL,
 ward_name VARCHAR(150) NOT NULL,
 street_address VARCHAR(300) NOT NULL,
 is_default BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_customer_addresses_user ON customer_addresses(user_id);
INSERT INTO customer_addresses(user_id,label,recipient_name,recipient_phone,province_id,province_name,district_id,district_name,ward_code,ward_name,street_address,is_default)
 SELECT id,'Địa chỉ mặc định',full_name,phone,default_province_id,default_province_name,default_district_id,default_district_name,default_ward_code,default_ward_name,default_street_address,TRUE
 FROM users WHERE default_province_id IS NOT NULL AND default_district_id IS NOT NULL AND default_ward_code IS NOT NULL AND default_street_address IS NOT NULL;
