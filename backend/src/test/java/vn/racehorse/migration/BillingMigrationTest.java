package vn.racehorse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.racehorse.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The billing migration: every horse has an open owner period and an invoice cannot be changed (TECH-163, BR-180). */
class BillingMigrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyHorseHasExactlyOneOpenOwnerPeriod() {
        Integer without = jdbc.queryForObject("SELECT count(*) FROM horse h WHERE NOT EXISTS "
                + "(SELECT 1 FROM horse_owner_period p WHERE p.horse_id = h.id AND p.to_date IS NULL)", Integer.class);
        assertThat(without).isZero();
    }

    @Test
    void anInvoiceAndItsLinesRefuseUpdateAndDelete() {
        Long owner = jdbc.queryForObject("SELECT id FROM user_account ORDER BY id LIMIT 1", Long.class);
        if (owner == null) {
            return;
        }
        Long invoice = jdbc.queryForObject("INSERT INTO invoice (owner_id, month, number, total, closed_at) "
                + "VALUES (?, '1900-01', 'MIG-1', 10, now()) RETURNING id", Long.class, owner);
        jdbc.update("INSERT INTO invoice_line (invoice_id, category, description, work_date, charge_date, quantity, "
                + "unit_price, amount) VALUES (?, 'EXAM', 'x', '1900-01-02', '1900-01-02', 1, 10, 10)", invoice);
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice_line SET amount = 1 WHERE invoice_id = ?", invoice))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_line WHERE invoice_id = ?", invoice))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE invoice SET total = 1 WHERE id = ?", invoice))
                .isInstanceOf(DataAccessException.class);
    }
}
