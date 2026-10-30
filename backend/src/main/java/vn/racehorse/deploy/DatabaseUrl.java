package vn.racehorse.deploy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the database variables a hosting platform provides into JDBC settings (TECH-109).
 * Order: an explicit {@code DB_URL} wins (nothing to do), then {@code DATABASE_URL}, then the {@code PG*} variables.
 * Pure logic so it can be unit tested; the password never appears in a message or in {@link #toString()}.
 */
public final class DatabaseUrl {

    private static final Pattern URL = Pattern.compile(
            "^postgres(?:ql)?://(?:([^:@/]*)(?::([^@/]*))?@)?(\\[[^\\]]+\\]|[^:/?#]+)(?::(\\d+))?(?:/([^?#]*))?(?:\\?(.*))?$");

    /** JDBC address, user and password ready for {@code spring.datasource.*}. */
    public record Settings(String url, String username, String password, String source) {
        @Override
        public String toString() {
            return "Settings[url=" + url + ", username=" + username + ", source=" + source + "]";
        }
    }

    private DatabaseUrl() {
    }

    /** Empty when {@code DB_URL} is set or when the platform provided nothing (local defaults apply). */
    public static Optional<Settings> from(Map<String, String> env) {
        if (present(env.get("DB_URL"))) {
            return Optional.empty();
        }
        if (present(env.get("DATABASE_URL"))) {
            return Optional.of(parseUrl(env.get("DATABASE_URL").trim()));
        }
        if (present(env.get("PGHOST"))) {
            String port = present(env.get("PGPORT")) ? env.get("PGPORT").trim() : "5432";
            String database = present(env.get("PGDATABASE")) ? env.get("PGDATABASE").trim() : "postgres";
            return Optional.of(new Settings(
                    "jdbc:postgresql://" + env.get("PGHOST").trim() + ":" + port + "/" + database,
                    present(env.get("PGUSER")) ? env.get("PGUSER").trim() : "postgres",
                    env.getOrDefault("PGPASSWORD", ""),
                    "PG variables"));
        }
        return Optional.empty();
    }

    private static Settings parseUrl(String value) {
        Matcher m = URL.matcher(value);
        if (!m.matches()) {
            // The value is not echoed: it contains the password.
            throw new IllegalArgumentException(
                    "DATABASE_URL is not of the form postgresql://user:password@host:port/database");
        }
        String user = m.group(1) == null || m.group(1).isEmpty() ? "postgres" : decode(m.group(1));
        String password = m.group(2) == null ? "" : decode(m.group(2));
        String port = m.group(4) == null ? "5432" : m.group(4);
        String database = m.group(5) == null || m.group(5).isEmpty() ? "postgres" : m.group(5);
        String query = m.group(6) == null || m.group(6).isEmpty() ? "" : "?" + m.group(6);
        return new Settings("jdbc:postgresql://" + m.group(3) + ":" + port + "/" + database + query,
                user, password, "DATABASE_URL");
    }

    /** Percent-decoding only: unlike {@code URLDecoder}, a plus sign in a password stays a plus sign. */
    static String decode(String raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                out.write(Integer.parseInt(new String(bytes, i + 1, 2, StandardCharsets.US_ASCII), 16));
                i += 2;
            } else {
                out.write(bytes[i]);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
