CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    payment_method VARCHAR(30) NOT NULL,
    txn_ref VARCHAR(100) NOT NULL UNIQUE,
    transaction_code VARCHAR(100),
    amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    response_code VARCHAR(10),
    bank_code VARCHAR(20),
    pay_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_transaction_method CHECK (payment_method IN ('VNPAY', 'SIMULATED')),
    CONSTRAINT ck_payment_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payment_transactions_order ON payment_transactions(order_id);
CREATE INDEX idx_payment_transactions_code ON payment_transactions(transaction_code);
