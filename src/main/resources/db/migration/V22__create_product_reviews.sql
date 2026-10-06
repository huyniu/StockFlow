CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    customer_id BIGINT NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    service_rating INTEGER NOT NULL CHECK (service_rating BETWEEN 1 AND 5),
    comment VARCHAR(2000) NOT NULL CHECK (LENGTH(TRIM(comment)) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_review_purchase UNIQUE (order_id, product_id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_id, product_id) REFERENCES order_items(order_id, product_id)
);
CREATE INDEX ix_product_reviews_product_created ON product_reviews(product_id, created_at, id);
