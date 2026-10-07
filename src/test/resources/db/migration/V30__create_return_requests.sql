CREATE TABLE return_requests (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
 customer_id BIGINT NOT NULL REFERENCES users(id),
 kind VARCHAR(20) NOT NULL,
 reason VARCHAR(2000) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 resolution_note VARCHAR(2000),
 reviewed_by BIGINT REFERENCES users(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_returns_customer ON return_requests(customer_id,created_at);
CREATE TABLE return_request_images (
 id BIGSERIAL PRIMARY KEY,
 request_id BIGINT NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
 mime_type VARCHAR(30) NOT NULL,
 image_data BYTEA NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_return_images_request ON return_request_images(request_id);
