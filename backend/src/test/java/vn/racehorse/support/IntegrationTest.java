package vn.racehorse.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import vn.racehorse.common.TestClock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: one PostgreSQL container shared by every test class (TECH-005).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClock.Config.class)
@TestPropertySource(properties = {
        "app.bootstrap.username=admin",
        "app.bootstrap.password=admin-test-password",
        "spring.main.allow-bean-definition-overriding=true",
        "app.jobs.enabled=false",
        "app.video.dir=${java.io.tmpdir}/racehorse-test-videos",
        "app.photo.dir=${java.io.tmpdir}/racehorse-test-photos",
        // The shared database fills up over a test run: tests written before paging read whole lists in one page.
        "app.paging.max-size=100000"
})
public abstract class IntegrationTest {

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
