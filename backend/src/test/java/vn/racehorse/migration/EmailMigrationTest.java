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
 * V22 replaces the username by an email without losing an account: every existing account keeps its row, role and
 * password, has no email yet (BR-212) and a password version of 0; the database itself rejects a duplicate, an
 * upper-case or a malformed email (TECH-221). Runs on its own database: data is loaded at V21, then migrated.
 */
class EmailMigrationTest {

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
    void accountsKeepTheirRowsAndLoseTheirUsernames() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, "21").migrate();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                exec(c, "insert into user_account (id, username, display_name, password_hash, role, created_at) "
                        + "values (1, 'admin', 'Quản lý', 'hash-1', 'CLUB_MANAGER', now()), "
                        + "(2, 'groom', 'Groom', 'hash-2', 'GROOM', now())");
            }

            flyway(pg, null).migrate();

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                ResultSet rows = s.executeQuery(
                        "select id, email, password_hash, role, password_version from user_account order by id");
                rows.next();
                assertThat(rows.getLong(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isNull();
                assertThat(rows.getString(3)).isEqualTo("hash-1");
                assertThat(rows.getString(4)).isEqualTo("CLUB_MANAGER");
                assertThat(rows.getLong(5)).isZero();
                rows.next();
                assertThat(rows.getString(2)).isNull();
                assertThat(rows.getString(3)).isEqualTo("hash-2");
                assertThat(rows.next()).isFalse();

                ResultSet cols = s.executeQuery("select count(*) from information_schema.columns "
                        + "where table_name = 'user_account' and column_name = 'username'");
                cols.next();
                assertThat(cols.getInt(1)).isZero();

                // Several accounts without an email are fine; two with the same email are not.
                exec(c, "update user_account set email = 'boss@racehorse.example' where id = 1");
                assertThatThrownBy(() -> exec(c, "update user_account set email = 'boss@racehorse.example' where id = 2"))
                        .hasMessageContaining("user_account_email_unique");
                assertThatThrownBy(() -> exec(c, "update user_account set email = 'Groom@Racehorse.Example' where id = 2"))
                        .hasMessageContaining("user_account_email_shape");
                for (String bad : new String[]{"groom@", "gr oom@racehorse.example", "groom", "a@b"}) {
                    assertThatThrownBy(() -> exec(c, "update user_account set email = '" + bad + "' where id = 2"))
                            .as(bad).hasMessageContaining("user_account_email_shape");
                }
                exec(c, "update user_account set email = 'groom@racehorse.example' where id = 2");
            }
        }
    }

    @Test
    void onlyOneLiveRecoveryCodePerAccount() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, null).migrate();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                exec(c, "insert into user_account (id, email, display_name, password_hash, role, created_at) "
                        + "values (1, 'a@racehorse.example', 'A', 'x', 'GROOM', now())");
                exec(c, "insert into password_recovery (account_id, code_hash, created_at, expires_at, state) "
                        + "values (1, 'h', now(), now() + interval '5 minutes', 'LIVE')");
                assertThatThrownBy(() -> exec(c, "insert into password_recovery (account_id, code_hash, created_at, "
                        + "expires_at, state) values (1, 'h2', now(), now() + interval '5 minutes', 'LIVE')"))
                        .hasMessageContaining("password_recovery_live");
                exec(c, "insert into password_recovery (account_id, code_hash, created_at, expires_at, state) "
                        + "values (1, 'h3', now(), now() + interval '5 minutes', 'VOID')");
            }
        }
    }
}
