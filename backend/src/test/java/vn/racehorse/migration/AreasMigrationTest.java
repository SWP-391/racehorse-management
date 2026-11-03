package vn.racehorse.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 turns the free-text stalls into areas with a capacity without losing a horse: a stall whose number is a whole
 * number from 1 to 200 and is the first for its slot is placed, every other one is kept as legacy text (TECH-141).
 * Runs on its own database: data is loaded at V16 (before the change) and then migrated to the latest version.
 */
class AreasMigrationTest {

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

    private static long horse(Connection c, String reg, String area, String number) throws SQLException {
        String stall = area == null ? "NULL, NULL" : "'" + area + "', '" + number + "'";
        try (Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("insert into horse (name, registration_number, sex, birth_date, owner_id, "
                    + "created_at, updated_at, stall_area, stall_number) values ('" + reg + "', '" + reg
                    + "', 'MALE', '2020-01-01', 1, now(), now(), " + stall + ") returning id");
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void stallsBecomeAreasAndNothingIsLost() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")) {
            pg.start();
            flyway(pg, "16").migrate();
            long h1;
            long h2;
            long h3;
            long h4;
            long h5;
            long h6;
            long h7;
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                exec(c, "insert into user_account (id, username, display_name, password_hash, role, created_at) "
                        + "values (1, 'owner', 'Owner', 'x', 'HORSE_OWNER', now())");
                h1 = horse(c, "H1", "Khu A", "1");
                h2 = horse(c, "H2", "Khu A", "2");
                h3 = horse(c, "H3", "Khu A", "3");
                h4 = horse(c, "H4", "khu a", "03");
                h5 = horse(c, "H5", "Khu B", "B-2");
                h6 = horse(c, "H6", "Khu C", "500");
                h7 = horse(c, "H7", null, null);
                exec(c, "insert into user_account (id, username, display_name, password_hash, role, created_at) "
                        + "values (2, 'groom', 'Groom', 'x', 'GROOM', now())");
                exec(c, "insert into chore_entry (horse_id, chore_date, chore, groom_id, marked_at) values ("
                        + h1 + ", current_date, 'FEEDING', 2, now())");
            }

            flyway(pg, null).migrate();

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement()) {
                Map<String, Integer> capacity = new LinkedHashMap<>();
                ResultSet areas = s.executeQuery("select name, capacity from area order by name");
                while (areas.next()) {
                    capacity.put(areas.getString(1), areas.getInt(2));
                }
                assertThat(capacity).containsEntry("Khu A", 6).containsEntry("Khu B", 6).containsEntry("Khu C", 6)
                        .hasSize(3);

                Map<Long, String> placed = new LinkedHashMap<>();
                Map<Long, String> legacy = new LinkedHashMap<>();
                ResultSet rows = s.executeQuery("select h.id, a.name, h.stall_slot, h.legacy_stall from horse h "
                        + "left join area a on a.id = h.area_id order by h.id");
                while (rows.next()) {
                    if (rows.getString(2) != null) {
                        placed.put(rows.getLong(1), rows.getString(2) + "/" + rows.getInt(3));
                    }
                    if (rows.getString(4) != null) {
                        legacy.put(rows.getLong(1), rows.getString(4));
                    }
                }
                assertThat(placed).containsEntry(h1, "Khu A/1").containsEntry(h2, "Khu A/2")
                        .containsEntry(h3, "Khu A/3").hasSize(3);
                assertThat(legacy.keySet()).containsExactlyInAnyOrder(h4, h5, h6);
                assertThat(legacy.get(h4)).contains("03");
                assertThat(legacy.get(h5)).isEqualTo("Khu B, ô B-2");
                assertThat(legacy.get(h6)).isEqualTo("Khu C, ô 500");
                assertThat(placed).doesNotContainKey(h7);
                assertThat(legacy).doesNotContainKey(h7);

                ResultSet setting = s.executeQuery("select value from club_setting where key = 'leave_notice_days'");
                setting.next();
                assertThat(setting.getString(1)).isEqualTo("7");
                ResultSet chores = s.executeQuery("select on_behalf from chore_entry");
                chores.next();
                assertThat(chores.getBoolean(1)).isFalse();
                // The old columns and the old unique index are gone, the new one forbids a second horse per slot.
                ResultSet cols = s.executeQuery("select count(*) from information_schema.columns "
                        + "where table_name = 'horse' and column_name in ('stall_area', 'stall_number')");
                cols.next();
                assertThat(cols.getInt(1)).isZero();
                try {
                    s.execute("update horse set area_id = (select id from area where name = 'Khu A'), stall_slot = 1 "
                            + "where id = " + h4);
                    throw new AssertionError("a second horse in one slot must be refused");
                } catch (SQLException expected) {
                    assertThat(expected.getMessage()).contains("horse_stall_unique");
                }
            }
        }
    }
}
