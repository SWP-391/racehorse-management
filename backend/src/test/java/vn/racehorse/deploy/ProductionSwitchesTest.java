package vn.racehorse.deploy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.racehorse.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-11, D-12: the switches the Docker image sets as environment variables (TECH-110). The session cookie itself is
 * written by the servlet container, so the end-to-end cookie check is done against the running image (task 5.3).
 */
@TestPropertySource(properties = {
        "SESSION_COOKIE_SECURE=true",
        "FORWARD_HEADERS_STRATEGY=native",
        "SPRINGDOC_ENABLED=false",
        "SERVER_COMPRESSION_ENABLED=true",
        "PORT=3000"
})
class ProductionSwitchesTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    Environment env;

    @Test
    void D_11_theSwitchesReachTheServerConfiguration() {
        assertThat(env.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(env.getProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
        assertThat(env.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(env.getProperty("server.port")).isEqualTo("3000");
        assertThat(env.getProperty("server.compression.enabled", Boolean.class)).isTrue();
        assertThat(env.getProperty("server.compression.mime-types")).contains("model/gltf-binary");
    }

    @Test
    void D_12_theApiDocumentationIsOffWhenSwitchedOff() throws Exception {
        int status = mvc.perform(get("/api/openapi")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/swagger-ui/swagger-initializer.js")).andExpect(status().isNotFound());
    }

    @Test
    void D_13_onlyHealthIsExposed() throws Exception {
        assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
