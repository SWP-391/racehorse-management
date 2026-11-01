package vn.racehorse.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.racehorse.account.entity.Role;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.support.ApiTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A refusal made by the framework (no such path, wrong method or media type) has the app's error shape (TECH-190). */
class FrameworkErrorsTest extends ApiTest {

    private AccountPrincipal manager;

    @BeforeEach
    void signIn() {
        manager = account(Role.CLUB_MANAGER);
    }

    @Test
    void anUnknownApiPathAnswers404WithCodeAndVietnameseMessage() throws Exception {
        as(manager, get("/api/khong-co-duong-dan-nay")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy trang hoặc dữ liệu được yêu cầu."))
                .andExpect(jsonPath("$.fields").isMap());
    }

    @Test
    void aWrongMethodAnswers405() throws Exception {
        as(manager, delete("/api/horses")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void aWrongMediaTypeAnswers415() throws Exception {
        as(manager, post("/api/horses").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anUnacceptableAcceptHeaderAnswers406() throws Exception {
        as(manager, get("/api/horses").accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void aMalformedBodyAnswers400() throws Exception {
        as(manager, post("/api/horses").contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void aRequestWithoutTheCsrfTokenAnswers403InTheAppShape() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/horses/1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
