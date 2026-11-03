package vn.racehorse.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import vn.racehorse.account.entity.Role;
import vn.racehorse.support.ApiTest;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A request with nobody signed in is refused with the usual JSON error, never an empty answer (BR-191, AC-282). */
class SessionRefusalTest extends ApiTest {

    @Test
    void AC_282_requestWithoutSessionIsRefusedWithTheVietnameseJsonMessage() throws Exception {
        mvc.perform(get("/api/horses"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Vui lòng đăng nhập."))
                .andExpect(jsonPath("$.fields").isMap());
        mvc.perform(post("/api/notifications/read-all").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void AC_281_publicPathsStayPublicAndAWrongPasswordKeepsItsOwnAnswer() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/csrf")).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", "nobody", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(Matchers.not("UNAUTHENTICATED")));
    }

    @Test
    void BR_090_deactivatedAccountKeepsItsOwnMessage() throws Exception {
        var manager = account(Role.CLUB_MANAGER);
        JsonNode created = body(asJson(manager, post("/api/accounts"), Map.of(
                "username", unique("staff"), "displayName", "Nhân viên mới", "password", PASSWORD,
                "roles", List.of("GROOM"))).andExpect(status().isCreated()));
        MockHttpSession open = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", created.get("username").asText(),
                                "password", PASSWORD))))
                .andReturn().getRequest().getSession(false);
        as(manager, post("/api/accounts/" + created.get("id").asLong() + "/deactivate")).andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").session(open))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DEACTIVATED"))
                .andExpect(jsonPath("$.message").value("Tài khoản đã bị vô hiệu hóa."));
    }
}
