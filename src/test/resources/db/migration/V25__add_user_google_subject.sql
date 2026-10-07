ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);
CREATE UNIQUE INDEX idx_users_google_subject ON users(google_subject);
