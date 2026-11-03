package vn.racehorse.deploy.config;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-08, D-09, D-10: the database settings a hosting platform provides (TECH-109). */
class DatabaseUrlTest {

    @Test
    void D_08_databaseUrlFormIsConvertedAndDecoded() {
        Optional<DatabaseUrl.Settings> s = DatabaseUrl.from(
                Map.of("DATABASE_URL", "postgresql://app:p%40ss%2Bw%3Ard@db.internal:5433/railway"));
        assertThat(s).isPresent();
        assertThat(s.get().url()).isEqualTo("jdbc:postgresql://db.internal:5433/railway");
        assertThat(s.get().username()).isEqualTo("app");
        assertThat(s.get().password()).isEqualTo("p@ss+w:rd");
    }

    @Test
    void D_08_postgresSchemeDefaultPortAndQueryString() {
        DatabaseUrl.Settings s = DatabaseUrl.from(
                Map.of("DATABASE_URL", "postgres://u:plus+sign@host.example/dbname?sslmode=require")).orElseThrow();
        assertThat(s.url()).isEqualTo("jdbc:postgresql://host.example:5432/dbname?sslmode=require");
        assertThat(s.password()).isEqualTo("plus+sign");
    }

    @Test
    void D_08_aMalformedUrlFailsWithoutEchoingThePassword() {
        assertThatThrownBy(() -> DatabaseUrl.from(Map.of("DATABASE_URL", "mysql://u:secret-value@h/db")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DATABASE_URL")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void D_09_pgVariablesAreUsedWhenThereIsNoUrl() {
        DatabaseUrl.Settings s = DatabaseUrl.from(Map.of(
                "PGHOST", "postgres.railway.internal", "PGPORT", "5432", "PGUSER", "postgres",
                "PGPASSWORD", "pw", "PGDATABASE", "railway")).orElseThrow();
        assertThat(s.url()).isEqualTo("jdbc:postgresql://postgres.railway.internal:5432/railway");
        assertThat(s.username()).isEqualTo("postgres");
        assertThat(s.password()).isEqualTo("pw");
        assertThat(s.toString()).doesNotContain("pw]");
    }

    @Test
    void D_10_explicitDbUrlWinsAndNothingSetKeepsLocalDefaults() {
        assertThat(DatabaseUrl.from(Map.of("DB_URL", "jdbc:postgresql://x/y", "DATABASE_URL", "postgresql://a:b@h/d")))
                .isEmpty();
        assertThat(DatabaseUrl.from(Map.of())).isEmpty();
    }

    @Test
    void D_10_postProcessorAddsTheDatasourcePropertiesOnlyWhenNeeded() {
        MockEnvironment env = new MockEnvironment().withProperty("DATABASE_URL", "postgresql://app:pw@h:5432/d");
        new DatabaseEnvironmentPostProcessor(destination -> LogFactory.getLog("test")).postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h:5432/d");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("app");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("pw");

        MockEnvironment explicit = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://x/y").withProperty("DATABASE_URL", "postgresql://a:b@h/d");
        new DatabaseEnvironmentPostProcessor(destination -> LogFactory.getLog("test")).postProcessEnvironment(explicit, null);
        assertThat(explicit.getProperty("spring.datasource.url")).isNull();
    }
}
