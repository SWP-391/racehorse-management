package vn.racehorse.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** V20 starts the registration sequence after the largest existing NH-YYYY-NNNN suffix (AC-003, BR-193). */
class HorseNumberMigrationTest {

    private static void horse(Statement s, String reg) throws Exception {
        s.execute("insert into horse (name, registration_number, sex, birth_date, owner_id, created_at, updated_at) "
                + "values ('" + reg + "', '" + reg + "', 'MALE', '2020-01-01', 1, now(), now())");
    }

    private static long next(PostgreSQLContainer<?> pg) throws Exception {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("select nextval('horse_number_seq')");
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> pg, String target) {
        var config = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @Test
    void AC_003_sequenceStartsAfterTheLargestExistingGeneratedSuffix() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, "19").migrate();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                s.execute("insert into user_account (id, username, display_name, password_hash, role, created_at) "
                        + "values (1, 'owner', 'Owner', 'x', 'HORSE_OWNER', now())");
                horse(s, "NH-2026-0099");
                horse(s, "NH-2025-0007");
                horse(s, "ABC-123456");
            }
            flyway(pg, null).migrate();
            assertThat(next(pg)).isEqualTo(100);
        }
    }

    @Test
    void sequenceStartsAtOneWithoutGeneratedNumbers() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, null).migrate();
            assertThat(next(pg)).isEqualTo(1);
        }
    }
}
