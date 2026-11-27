package vn.racehorse.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.racehorse.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * The sign-ins of a person kept beside the HTTP session so they can be listed and ended one by one (BR-254, TECH-F8).
 * A session with no row is adopted on its first request; a revoked row ends its session.
 */
@Service
public class LoginSessionService {

    /** A session unused for longer than the container timeout has expired, so it is not listed. */
    private final Duration live;
    private static final Duration TOUCH_EVERY = Duration.ofMinutes(1);

    public record Row(long id, String userAgent, Instant createdAt, Instant lastUsedAt, boolean current) {
    }

    private final JdbcTemplate jdbc;

    public LoginSessionService(JdbcTemplate jdbc,
                               @org.springframework.beans.factory.annotation.Value("${server.servlet.session.timeout:30m}") Duration live) {
        this.jdbc = jdbc;
        this.live = live;
    }

    static String hash(String sessionId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String agent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? null : (ua.length() > 300 ? ua.substring(0, 300) : ua);
    }

    /** Records the session of a sign-in that has just been saved. */
    public void open(long userId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            insert(userId, hash(session.getId()), agent(request));
        }
    }

    private void insert(long userId, String hash, String agent) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into login_session (user_id, session_hash, user_agent, created_at, last_used_at) "
                + "values (?, ?, ?, ?, ?) on conflict (session_hash) do nothing", userId, hash, agent, now, now);
    }

    /**
     * Called for every request of a signed-in session. Returns false when this session was ended from another one;
     * the row is then removed. An unknown session is adopted, and the last use is written at most once a minute.
     */
    public boolean useOrEnd(long userId, HttpServletRequest request, HttpSession session) {
        String hash = hash(session.getId());
        List<Instant[]> rows = jdbc.query("select revoked_at, last_used_at from login_session where session_hash = ? and user_id = ?",
                (rs, i) -> new Instant[]{rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2).toInstant()}, hash, userId);
        if (rows.isEmpty()) {
            insert(userId, hash, agent(request));
            return true;
        }
        if (rows.get(0)[0] != null) {
            jdbc.update("delete from login_session where session_hash = ?", hash);
            return false;
        }
        if (rows.get(0)[1].isBefore(Instant.now().minus(TOUCH_EVERY))) {
            jdbc.update("update login_session set last_used_at = ? where session_hash = ?", Timestamp.from(Instant.now()), hash);
        }
        return true;
    }

    /** The live sign-ins of the person, the one of this request marked. Expired rows are not listed; purge() removes them. */
    public List<Row> list(long userId, HttpSession current) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(live));
        String mine = hash(current.getId());
        return jdbc.query("select id, user_agent, created_at, last_used_at, session_hash from login_session "
                        + "where user_id = ? and revoked_at is null and last_used_at >= ? order by last_used_at desc",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(), mine.equals(rs.getString(5))), userId, cutoff);
    }

    /** Global purge: revoked rows and rows unused for more than a day (long past the container timeout). */
    public int purge() {
        return jdbc.update("delete from login_session where revoked_at is not null or last_used_at < ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))));
    }

    /** Ends another sign-in of the person; the current one cannot be ended this way (use sign-out). */
    public void end(long userId, long id, HttpSession current) {
        String mine = hash(current.getId());
        int changed = jdbc.update("update login_session set revoked_at = ? where id = ? and user_id = ? "
                + "and session_hash <> ? and revoked_at is null", Timestamp.from(Instant.now()), id, userId, mine);
        if (changed == 0) {
            Boolean own = jdbc.queryForObject("select exists (select 1 from login_session where id = ? and user_id = ? and session_hash = ?)",
                    Boolean.class, id, userId, mine);
            if (Boolean.TRUE.equals(own)) {
                throw ApiException.invalid("CURRENT_SESSION", "id", "Đây là phiên hiện tại, hãy dùng Đăng xuất.");
            }
            throw ApiException.notFound("phiên đăng nhập");
        }
    }

    /** After a password change every other row goes; those sessions already end by the password version. */
    public void endOthers(long userId, HttpSession current) {
        if (current == null) {
            return;
        }
        jdbc.update("delete from login_session where user_id = ? and session_hash <> ?", userId, hash(current.getId()));
    }

    /** Sign-out removes the row of the session being closed. */
    public void close(HttpSession session) {
        jdbc.update("delete from login_session where session_hash = ?", hash(session.getId()));
    }
}
