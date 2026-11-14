package vn.racehorse.account;

import vn.racehorse.common.exception.ApiException;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The one place that reads an email address (BR-210, BR-211, TECH-222): trimmed, lower case, at most 254 characters,
 * the shape {@code local@domain.tld}. Every entry point (account create and email entry, sign-in, recovery, bootstrap)
 * goes through it, so two spellings of one address are always the same account.
 */
public final class EmailAddress {

    public static final int MAX_LENGTH = 254;
    public static final String INVALID = "Email không hợp lệ.";

    private static final Pattern SHAPE = Pattern.compile(
            "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$", Pattern.UNICODE_CHARACTER_CLASS);

    private final String value;

    private EmailAddress(String value) {
        this.value = value;
    }

    /** The normalised address, or a refusal that names the email field. */
    public static EmailAddress parse(String raw) {
        return tryParse(raw).orElseThrow(() -> ApiException.invalid("INVALID_EMAIL", "email", INVALID));
    }

    public static Optional<EmailAddress> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.length() > MAX_LENGTH || !SHAPE.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(new EmailAddress(text));
    }

    public String value() {
        return value;
    }

    /** For the audit log and the logs: {@code t***@example.com}. */
    public String masked() {
        return mask(value);
    }

    public static String mask(String email) {
        if (email == null || email.isEmpty()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Override
    public String toString() {
        return masked();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmailAddress e && e.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
