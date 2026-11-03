package vn.racehorse.deploy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import vn.racehorse.support.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D-03: without a bundled client (local development) nothing about the web client is served. */
class NoClientTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void D_03_unknownNonApiPathsAreRefusedAsBefore() throws Exception {
        mvc.perform(get("/horses/12")).andExpect(status().isUnauthorized());
        mvc.perform(get("/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/")).andExpect(status().isUnauthorized());
    }
}
