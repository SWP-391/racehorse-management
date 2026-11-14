package vn.racehorse.account;

import org.junit.jupiter.api.Test;
import vn.racehorse.common.exception.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One reader for every email (BR-210, BR-211, TECH-222). */
class EmailAddressTest {

    @Test
    void trimsAndLowerCases() {
        assertThat(EmailAddress.parse(" Tuan.Nguyen@Example.com ").value()).isEqualTo("tuan.nguyen@example.com");
        assertThat(EmailAddress.parse("AN.LE+club@Mail.Example.VN").value()).isEqualTo("an.le+club@mail.example.vn");
    }

    @Test
    void refusesWhatIsNotAnEmailWithTheFieldNamed() {
        for (String bad : new String[]{"an.le@", "@example.com", "an le@example.com", "a@b", "a@@b.com", "a@b..com",
                "a@b.com.", "a@.com", "", "   ", "no-at-sign", "two@at@example.com"}) {
            assertThatThrownBy(() -> EmailAddress.parse(bad)).as(bad).isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException api = (ApiException) e;
                        assertThat(api.code()).isEqualTo("INVALID_EMAIL");
                        assertThat(api.fields()).containsEntry("email", "Email không hợp lệ.");
                    });
        }
        assertThatThrownBy(() -> EmailAddress.parse(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void refusesMoreThan254Characters() {
        String local = "a".repeat(242);
        assertThat(EmailAddress.tryParse(local + "@example.com")).isPresent(); // 254 characters
        assertThat(EmailAddress.tryParse(local + "b@example.com")).isEmpty();  // 255 characters
    }

    @Test
    void masksForTheAuditAndTheLogs() {
        assertThat(EmailAddress.parse("tuan@example.com").masked()).isEqualTo("t***@example.com");
        assertThat(EmailAddress.parse("tuan@example.com").toString()).doesNotContain("tuan@");
        assertThat(EmailAddress.mask(null)).isEmpty();
    }

    @Test
    void twoSpellingsOfOneAddressAreEqual() {
        assertThat(EmailAddress.parse("A@B.com")).isEqualTo(EmailAddress.parse(" a@b.COM "));
    }
}
