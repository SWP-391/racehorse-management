package vn.racehorse.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** While the key is not configured every notice is refused, whatever it carries (fail closed, BR-225, TECH-234). */
@TestPropertySource(properties = "app.payment.webhook-api-key=")
class NoticeKeyUnsetTest extends PaymentTestBase {

    @Test
    void anUnsetKeyRefusesEveryNotice() throws Exception {
        String id = nextTransferId();
        send("Apikey ", notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        send("Apikey anything", notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        send(null, notice(id, 10_000, "x")).andExpect(status().isUnauthorized());
        assertThat(countTransfers(id)).isZero();
    }
}
