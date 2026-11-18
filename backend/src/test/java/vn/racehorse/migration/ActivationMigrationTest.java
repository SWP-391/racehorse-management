package vn.racehorse.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V26 adds the pending flag and the purpose of a recovery code without changing any account or code: an account stays
 * active and not pending, an old code is a reset code, and the database refuses an account that is pending and active at
 * once (TECH-231, TECH-233). Data is loaded at V25, then migrated.
 */
class ActivationMigrationTest {

    private static Flyway flyway(PostgreSQLContainer<?> pg, String target) {
        var config = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    void existingAccountsStayActiveAndOldCodesAreResets() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, "25").migrate();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                exec(c, "insert into user_account (id, email, display_name, password_hash, role, created_at) "
                        + "values (1, 'groom@racehorse.example', 'Groom', 'hash-1', 'GROOM', now())");
                exec(c, "insert into password_recovery (account_id, code_hash, created_at, expires_at, state) "
                        + "values (1, 'h', now(), now() + interval '5 minutes', 'LIVE')");
            }

            flyway(pg, null).migrate();

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                ResultSet account = s.executeQuery("select active, pending from user_account where id = 1");
                account.next();
                assertThat(account.getBoolean(1)).isTrue();
                assertThat(account.getBoolean(2)).isFalse();
                ResultSet code = s.executeQuery("select purpose from password_recovery where account_id = 1");
                code.next();
                assertThat(code.getString(1)).isEqualTo("RESET");

                assertThatThrownBy(() -> exec(c, "update user_account set pending = true where id = 1"))
                        .hasMessageContaining("user_account_pending_not_active");
                exec(c, "update user_account set pending = true, active = false where id = 1");
                exec(c, "update password_recovery set purpose = 'ACTIVATION' where account_id = 1");
                assertThatThrownBy(() -> exec(c, "update password_recovery set purpose = 'OTHER' where account_id = 1"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
