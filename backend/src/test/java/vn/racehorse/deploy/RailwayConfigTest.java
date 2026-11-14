package vn.racehorse.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** D-15: the Railway configuration and the guide exist and agree with each other. */
class RailwayConfigTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void D_15_railwayJsonNamesTheDockerfileBuilderAndTheHealthCheck() throws Exception {
        JsonNode json = new ObjectMapper().readTree(Files.readString(ROOT.resolve("railway.json")));
        assertThat(json.at("/build/builder").asText()).isEqualTo("DOCKERFILE");
        assertThat(json.at("/build/dockerfilePath").asText()).isEqualTo("Dockerfile");
        assertThat(json.at("/deploy/healthcheckPath").asText()).isEqualTo("/actuator/health");
        assertThat(json.at("/deploy/restartPolicyType").asText()).isEqualTo("ON_FAILURE");
        assertThat(Files.exists(ROOT.resolve("Dockerfile"))).isTrue();
    }

    @Test
    void D_15_theGuideListsEveryVariableTheServiceNeeds() throws Exception {
        String guide = Files.readString(ROOT.resolve("docs/TRIEN-KHAI-RAILWAY.md"));
        for (String variable : new String[]{"DATABASE_URL", "BOOTSTRAP_EMAIL", "BOOTSTRAP_PASSWORD", "CLUB_TIME_ZONE",
                "VIDEO_DIR", "RAILWAY_RUN_UID", "SESSION_COOKIE_SECURE", "FORWARD_HEADERS_STRATEGY", "SPRINGDOC_ENABLED",
                "JAVA_TOOL_OPTIONS"}) {
            assertThat(guide).contains(variable);
        }
        assertThat(guide).contains("railway up").contains("railway domain").contains("railway volume --service racehorse add");
    }

    @Test
    void D_02_theImageRunsAsANonRootUserWithProductionDefaults() throws Exception {
        String dockerfile = Files.readString(ROOT.resolve("Dockerfile"));
        assertThat(dockerfile).contains("USER app");
        assertThat(dockerfile).contains("SESSION_COOKIE_SECURE=true").contains("FORWARD_HEADERS_STRATEGY=native")
                .contains("SPRINGDOC_ENABLED=false").contains("MaxRAMPercentage");
    }
}
