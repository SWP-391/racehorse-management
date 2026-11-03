package vn.racehorse.deploy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.racehorse.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D-01, D-04, D-05, D-06, D-07: the bundled web client (a small fixture stands in for the real build). */
@TestPropertySource(properties = "app.web.frontend-location=classpath:/fixture-web/")
class ClientServingTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void D_01_theRootAndIndexAreServedAnonymously() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"root\">")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void D_04_aDeepLinkReturnsIndexHtml() throws Exception {
        mvc.perform(get("/horses/12/injuries")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<div id=\"root\">")));
    }

    @Test
    void D_05_aMissingFileIsNotIndexHtml() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/favicon-missing.ico")).andExpect(status().isNotFound());
    }

    @Test
    void D_05_anUnknownApiPathStaysProtected() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
    }

    @Test
    void D_06_cacheHeadersAndTheModelContentType() throws Exception {
        String assetCache = mvc.perform(get("/assets/app-abc123.js")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");
        assertThat(assetCache).contains("max-age=31536000").contains("public").contains("immutable");

        mvc.perform(get("/index.html")).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));

        mvc.perform(get("/models/horse.glb")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "model/gltf-binary"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")));
    }

    @Test
    void D_07_theApiStillRequiresSignIn() throws Exception {
        mvc.perform(get("/api/horses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/horses")).andExpect(status().is4xxClientError());
        mvc.perform(post("/anything")).andExpect(status().is4xxClientError());
    }

    @Test
    void D_13_onlyHealthIsExposedByTheActuator() throws Exception {
        int status = mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
    }
}
