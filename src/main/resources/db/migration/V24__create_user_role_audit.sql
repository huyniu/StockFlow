CREATE TABLE user_role_audit (
 id BIGSERIAL PRIMARY KEY,
 target_user_id BIGINT NOT NULL REFERENCES users(id),
 actor_user_id BIGINT NOT NULL REFERENCES users(id),
 old_role VARCHAR(30) NOT NULL,
 new_role VARCHAR(30) NOT NULL,
 old_warehouse_ids VARCHAR(1000) NOT NULL,
 new_warehouse_ids VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_role_audit_target_created ON user_role_audit(target_user_id, created_at DESC);
