CREATE TABLE order_notification_outbox (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
 event_type VARCHAR(30) NOT NULL,
 recipient VARCHAR(255) NOT NULL,
 subject VARCHAR(255) NOT NULL,
 message TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(order_id,event_type)
);
CREATE INDEX idx_order_mail_pending ON order_notification_outbox(sent_at,next_attempt_at);
