package vn.racehorse.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** The unit catalog migration keeps every unit already in use ("Units already in use are kept", BR-194). */
class SupplyUnitMigrationTest {

    private static Flyway flyway(PostgreSQLContainer<?> pg, String target) {
        var config = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static void item(Statement s, String name, String unit) throws Exception {
        s.execute("insert into supply_item (name, category, unit, quantity, unit_price, active, created_at) values ('"
                + name + "', 'FEED', '" + unit + "', 0, 0, true, now())");
    }

    private static String unitOf(Statement s, String name) throws Exception {
        ResultSet rs = s.executeQuery("select u.name from supply_item i join supply_unit u on u.id = i.unit_id "
                + "where i.name = '" + name + "'");
        rs.next();
        return rs.getString(1);
    }

    @Test
    void spellingsCollapseAndOddUnitsAreKept() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, "20").migrate();
            String longUnit = "đơn vị rất dài hơn hai mươi ký tự";
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                item(s, "a", "kg");
                item(s, "b", "Kg");
                item(s, "c", " kg ");
                item(s, "d", longUnit);
            }
            flyway(pg, null).migrate();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                assertThat(unitOf(s, "a")).isEqualTo("kg");
                assertThat(unitOf(s, "b")).isEqualTo("kg");
                assertThat(unitOf(s, "c")).isEqualTo("kg");
                assertThat(unitOf(s, "d")).isEqualTo(longUnit);
                ResultSet rs = s.executeQuery("select count(*) from supply_unit where lower(name) = 'kg'");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
                rs = s.executeQuery("select count(*) from supply_item where unit_id is null");
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }
}
