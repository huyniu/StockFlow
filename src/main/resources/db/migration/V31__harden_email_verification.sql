-- Không giữ mã rõ của các OTP đã phát. Khách chưa xác thực cần yêu cầu mã mới.
ALTER TABLE email_verification_tokens ADD COLUMN otp_hash VARCHAR(255);
ALTER TABLE email_verification_tokens ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE email_verification_tokens ADD COLUMN invalidated_at TIMESTAMP WITH TIME ZONE;
UPDATE email_verification_tokens
SET otp_hash = 'legacy-invalidated', invalidated_at = CURRENT_TIMESTAMP,
    expires_at = LEAST(expires_at, CURRENT_TIMESTAMP);
ALTER TABLE email_verification_tokens ALTER COLUMN otp_hash SET NOT NULL;
ALTER TABLE email_verification_tokens ADD CONSTRAINT chk_email_otp_attempts CHECK (attempts BETWEEN 0 AND 5);
DROP INDEX idx_email_verification_user_otp;
ALTER TABLE email_verification_tokens DROP COLUMN otp_code;
CREATE INDEX idx_email_verification_user_created ON email_verification_tokens(user_id, created_at, id);
