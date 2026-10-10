package com.stockflow.auth;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthHardeningMigrationTest {
    @Test void migrationInvalidatesPlaintextOtpPreservesVerificationAndDisablesPublicOperators() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:auth_migration_"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(source).locations("filesystem:src/test/resources/db/migration").target("30").load().migrate();
        var db=new JdbcTemplate(source);
        for(String email:new String[]{"pending@private.test","verified@private.test","admin@stockflow.com","manager@stockflow.com","staff.hn@stockflow.com","private-admin@private.test"}){
            String role=email.startsWith("admin@")||email.startsWith("private-admin@")?"ADMIN":email.startsWith("manager@")?"MANAGER":email.startsWith("staff.")?"WAREHOUSE_STAFF":"CUSTOMER";
            db.update("INSERT INTO users(email,password_hash,full_name,role_id,email_verified) VALUES (?,'hash','Migration',(SELECT id FROM roles WHERE name=?),?)",email,role,!email.startsWith("pending@"));
            db.update("INSERT INTO email_verification_tokens(user_id,otp_code,expires_at) SELECT id,'909090',DATEADD('MINUTE',15,CURRENT_TIMESTAMP) FROM users WHERE email=?",email);
        }
        var migration=Flyway.configure().dataSource(source).locations("filesystem:src/test/resources/db/migration").cleanDisabled(false).load();
        try{
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='email_verification_tokens' AND column_name='otp_code'",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM email_verification_tokens WHERE otp_hash='legacy-invalidated' AND invalidated_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP AND attempts=0",Integer.class)).isEqualTo(6);
            assertThat(db.queryForObject("SELECT email_verified FROM users WHERE email='pending@private.test'",Boolean.class)).isFalse();
            assertThat(db.queryForObject("SELECT email_verified FROM users WHERE email='verified@private.test'",Boolean.class)).isTrue();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE email IN ('admin@stockflow.com','manager@stockflow.com','staff.hn@stockflow.com') AND status='INACTIVE' AND auth_version=1",Integer.class)).isEqualTo(3);
            assertThat(db.queryForObject("SELECT status FROM users WHERE email='private-admin@private.test'",String.class)).isEqualTo("ACTIVE");
            assertThat(db.queryForObject("SELECT auth_version FROM users WHERE email='private-admin@private.test'",Long.class)).isZero();
            assertThat(migration.migrate().migrationsExecuted).isZero();
        }finally{migration.clean();}
    }

    @Test void newMigrationsMatchBetweenPostgresAndTestResources() throws Exception {
        for(String name:new String[]{"V31__harden_email_verification.sql","V32__disable_public_operator_demo_accounts.sql"})
            assertThat(Files.readString(Path.of("src/main/resources/db/migration",name)))
                    .isEqualTo(Files.readString(Path.of("src/test/resources/db/migration",name)));
    }
}
