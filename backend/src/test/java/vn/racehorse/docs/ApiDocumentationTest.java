package vn.racehorse.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import vn.racehorse.support.IntegrationTest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API documentation with the switch on: the page, the sign-in and CSRF description (TECH-201 to TECH-204). */
class ApiDocumentationTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ApplicationContext context;
    @Autowired
    ObjectMapper json;

    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void A_01_thePageOpensWithoutSignIn() throws Exception {
        assertThat(body("/api/swagger-ui/index.html")).contains("Swagger UI");
        mvc.perform(get("/api/swagger-ui")).andExpect(status().is3xxRedirection());
    }

    @Test
    void A_02_otherApiPathsStillNeedSignIn() throws Exception {
        mvc.perform(get("/api/horses")).andExpect(status().isUnauthorized());
    }

    @Test
    void A_03_theDescriptionExplainsSignInAndTheCsrfHeader() throws Exception {
        String spec = body("/api/openapi");
        assertThat(spec).contains("Quản lý ngựa đua - API").contains("/api/auth/login")
                .contains("\"csrf\"").contains("X-XSRF-TOKEN");
    }

    @Test
    void A_03b_theCsrfCallShowsNoParameters() throws Exception {
        JsonNode csrf = json.readTree(body("/api/openapi")).at("/paths/~1api~1auth~1csrf/get");
        assertThat(csrf.has("parameters")).as("parameters of GET /api/auth/csrf").isFalse();
    }

    @Test
    void A_04_writeCallsFromThePageCarryTheCsrfHeader() throws Exception {
        String initializer = body("/api/swagger-ui/swagger-initializer.js");
        assertThat(initializer).contains("XSRF-TOKEN").contains("X-XSRF-TOKEN");
    }

    /** TECH-206: a new controller or endpoint cannot skip the documentation. */
    @Test
    void A_05_everyControllerHasATag() {
        List<String> untagged = new ArrayList<>();
        for (Object bean : context.getBeansWithAnnotation(Controller.class).values()) {
            Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            if (type.getPackageName().startsWith("vn.racehorse") && type.getSimpleName().endsWith("Controller")
                    && AnnotationUtils.findAnnotation(type, Tag.class) == null) {
                untagged.add(type.getSimpleName());
            }
        }
        assertThat(untagged).as("controllers without @Tag").isEmpty();
    }

    @Test
    void A_06_everyEndpointHasASummaryAndAReadableGroup() throws Exception {
        JsonNode paths = json.readTree(body("/api/openapi")).get("paths");
        List<String> problems = new ArrayList<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            String name = op.getKey().toUpperCase() + " " + path.getKey();
            JsonNode node = op.getValue();
            if (!node.hasNonNull("summary") || node.get("summary").asText().isBlank()) {
                problems.add(name + " has no summary");
            }
            if (!node.has("tags") || node.get("tags").isEmpty() || node.get("tags").get(0).asText().endsWith("-controller")) {
                problems.add(name + " has no named group");
            }
        }));
        assertThat(problems).isEmpty();
        assertThat(paths.size()).isGreaterThan(100);
    }
}
