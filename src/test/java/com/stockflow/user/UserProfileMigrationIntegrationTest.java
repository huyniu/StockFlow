package com.stockflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Nâng V15 lên V16 trên H2 riêng, kiểm tra bảo toàn tài khoản cũ và ràng buộc số liên hệ ở DB. */
class UserProfileMigrationIntegrationTest {

    /** Không bịa số cho user cũ; cột định danh, hash, quyền, trạng thái và timestamp giữ nguyên. */
    @Test
    void migrationPreservesLegacyAccountsAndIsRepeatable() {
        var source = source();
        var migration = schema(source, "16");
        try {
            schema(source, "15").migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            var before = account(jdbc);
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(account(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("""
                    SELECT phone
                    FROM users
                    WHERE id = 90001
                    """, String.class)).isNull();
            assertThat(migration.migrate().migrationsExecuted).isZero();
        } finally {
            migration.clean();
        }
    }

    /** Bỏ qua API vẫn không thể lưu số sai hoặc chuỗi rỗng; null và số chuẩn hóa là hợp lệ. */
    @Test
    void databaseRejectsInvalidPhones() {
        var source = source();
        var migration = schema(source, "16");
        try {
            migration.migrate();
            var jdbc = new JdbcTemplate(source);
            seed(jdbc);
            for (String invalid : new String[] {"", "1234567", "1234567890123456", "090abcdefg", "++84901234567"}) {
                assertThatThrownBy(() -> setPhone(jdbc, invalid)).isInstanceOf(DataIntegrityViolationException.class);
            }
            assertThat(setPhone(jdbc, "+123456789012345")).isEqualTo(1);
            assertThat(setPhone(jdbc, null)).isEqualTo(1);
        } finally {
            migration.clean();
        }
    }

    /** Database tạm riêng cho bài migration; không đụng schema dùng bởi Spring hoặc database của người dùng. */
    private DriverManagerDataSource source() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:profile_migration_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
    }

    /** Chỉ nâng tới phiên bản cần kiểm chứng; clean chỉ dành cho H2 tạm do chính bài này tạo. */
    private Flyway schema(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source)
                .locations("filesystem:src/test/resources/db/migration")
                .target(target).cleanDisabled(false).load();
    }

    /** Tài khoản đã tồn tại trước V16, có role CUSTOMER và hash được giữ nguyên khi nâng cấp. */
    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role_id)
                SELECT 90001, 'legacy@profile.test', 'existing-hash', 'Khách cũ', id
                FROM roles
                WHERE name = 'CUSTOMER'
                """);
    }

    /** Chỉ chụp các cột V15 để so sánh sau khi V16 thêm cột mới. */
    private java.util.Map<String, Object> account(JdbcTemplate jdbc) {
        return jdbc.queryForMap("""
                SELECT id,
                       email,
                       password_hash,
                       full_name,
                       role_id,
                       status,
                       created_at,
                       updated_at
                FROM users
                WHERE id = 90001
                """);
    }

    /** Ghi SQL trực tiếp để xác nhận constraint vẫn có tác dụng ngoài controller/service. */
    private int setPhone(JdbcTemplate jdbc, String phone) {
        return jdbc.update("""
                UPDATE users
                SET phone = ?
                WHERE id = 90001
                """, phone);
    }
}
