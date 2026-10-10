-- Preserve identities, role assignments and all business history. Recovery is opt-in.
ALTER TABLE users ADD COLUMN operator_recovered_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE operator_recovery_runs (
    request_id VARCHAR(100) PRIMARY KEY,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
