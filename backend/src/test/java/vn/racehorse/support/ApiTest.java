package vn.racehorse.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.security.AccountPrincipal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for API tests: accounts per role, JSON requests as a given user, horses registered through the API. */
public abstract class ApiTest extends IntegrationTest {

    public static final String PASSWORD = "password-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected UserAccountRepository accounts;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected TransactionTemplate tx;

    private AccountPrincipal defaultTrainer;

    /** The Head Trainer given to every horse registered through {@link #horseBody} (BR-152). */
    protected AccountPrincipal defaultTrainer() {
        if (defaultTrainer == null) {
            defaultTrainer = account(Role.HEAD_TRAINER);
        }
        return defaultTrainer;
    }

    /** Makes the given Head Trainer the one assigned to horses registered from now on. */
    protected void useTrainer(AccountPrincipal trainer) {
        this.defaultTrainer = trainer;
    }

    @org.junit.jupiter.api.BeforeEach
    void forgetDefaultTrainer() {
        defaultTrainer = null;
    }

    protected static String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + SEQ.incrementAndGet();
    }

    /** Creates an active account directly in the database (test fixture, not under test). */
    protected AccountPrincipal account(Role role) {
        String username = unique(role.name().toLowerCase());
        UserAccount saved = accounts.save(new UserAccount(username, "Test " + username,
                passwordEncoder.encode(PASSWORD), role, Instant.now()));
        return AccountPrincipal.of(saved);
    }

    /**
     * Lists are paged (BR-185): a GET without `size` asks for every row (the test run raises the largest page), so
     * tests written before paging still see a whole list; the paging tests pass their own `size`.
     */
    protected ResultActions as(AccountPrincipal who, MockHttpServletRequestBuilder request) throws Exception {
        var probe = request.buildRequest(new org.springframework.mock.web.MockServletContext());
        if ("GET".equals(probe.getMethod()) && probe.getParameter("size") == null) {
            request.param("size", "100000");
        }
        return mvc.perform(request.with(user(who)).with(csrf()));
    }

    protected ResultActions asJson(AccountPrincipal who, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return mvc.perform(request.with(user(who)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }

    /** The body; a page of a list is unwrapped to its items, the page itself is read with {@link #pageBody}. */
    protected JsonNode body(ResultActions result) throws Exception {
        JsonNode node = pageBody(result);
        boolean page = node.isObject() && node.has("items") && node.has("total") && node.has("page")
                && node.has("size") && !node.has("unread");
        return page ? node.get("items") : node;
    }

    protected JsonNode pageBody(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    /** The id of a unit of the supply catalog, added when it does not exist yet. */
    protected long unitId(String name) throws Exception {
        AccountPrincipal manager = account(Role.CLUB_MANAGER);
        for (JsonNode u : body(as(manager, get("/api/supply-units")).andExpect(status().isOk()))) {
            if (u.get("name").asText().equals(name)) {
                return u.get("id").asLong();
            }
        }
        return body(asJson(manager, post("/api/supply-units"), Map.of("name", name)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    protected Map<String, Object> horseBody(String name, String sex, Long ownerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("sex", sex);
        body.put("birthDate", "2021-04-12");
        body.put("coatColour", "Hồng");
        body.put("breed", "Thoroughbred");
        body.put("ownerId", ownerId);
        body.put("trainerId", defaultTrainer().id());
        return body;
    }

    /** Registers a horse through the API as the given Club Manager and returns its id. */
    protected long registerHorse(AccountPrincipal manager, AccountPrincipal owner, String sex) throws Exception {
        ResultActions result = asJson(manager, post("/api/horses"),
                horseBody(unique("Horse"), sex, owner.id()))
                .andExpect(status().isCreated());
        return body(result).get("id").asLong();
    }
}
