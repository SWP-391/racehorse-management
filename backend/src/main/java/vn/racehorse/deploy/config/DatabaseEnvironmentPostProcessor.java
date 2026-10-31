package vn.racehorse.deploy.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets the service start on a platform that provides {@code DATABASE_URL} or {@code PG*} variables
 * (Railway does) without any extra setting (TECH-109). An explicit {@code DB_URL} keeps priority.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String[] KEYS = {"DB_URL", "DATABASE_URL", "PGHOST", "PGPORT", "PGUSER", "PGPASSWORD", "PGDATABASE"};

    private final Log log;

    public DatabaseEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> env = new HashMap<>();
        for (String key : KEYS) {
            String value = environment.getProperty(key);
            if (value != null) {
                env.put(key, value);
            }
        }
        DatabaseUrl.from(env).ifPresent(settings -> {
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.datasource.url", settings.url());
            properties.put("spring.datasource.username", settings.username());
            properties.put("spring.datasource.password", settings.password());
            environment.getPropertySources().addFirst(new MapPropertySource("platformDatabase", properties));
            log.info("Database settings taken from " + settings.source() + " (" + settings.url().replaceAll("\\?.*", "") + ")");
        });
    }
}
