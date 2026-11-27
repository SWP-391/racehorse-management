package vn.racehorse.ration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The four default ration templates of BR-265 / TECH-F9 on a database that started empty: Flyway V31 runs before the
 * first Club Manager and any feed item exist, so it adds nothing there. This runner (after BootstrapClubManager) runs
 * the same guarded block of V31 in one transaction under a PostgreSQL advisory lock, so two instances starting
 * together add the templates once. With no feed catalog yet nothing is invented (a known limit).
 */
@Component
@Order(DefaultRationTemplates.ORDER)
public class DefaultRationTemplates implements ApplicationRunner {

    public static final int ORDER = 20;
    private static final long LOCK_KEY = 7_310_031L;
    private static final Logger log = LoggerFactory.getLogger(DefaultRationTemplates.class);

    private final JdbcTemplate jdbc;
    private final String block;

    public DefaultRationTemplates(JdbcTemplate jdbc) throws IOException {
        this.jdbc = jdbc;
        String sql = new ClassPathResource("db/migration/V31__default_ration_templates.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int at = sql.indexOf("\nDO $$");
        if (at < 0) {
            throw new IllegalStateException("V31 has no DO block");
        }
        this.block = sql.substring(at + 1);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, LOCK_KEY);
        Integer before = jdbc.queryForObject("select count(*) from ration_template", Integer.class);
        jdbc.execute(block);
        Integer after = jdbc.queryForObject("select count(*) from ration_template", Integer.class);
        if (after != null && before != null && after > before) {
            log.info("added {} default ration templates", after - before);
        }
    }
}
