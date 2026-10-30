package vn.racehorse.common;

import java.text.Normalizer;
import java.util.Locale;

/** Search that ignores case and Vietnamese tone marks: "ngua", "NGỰA" and "Ngựa" are the same text (TECH-174). */
public final class Search {

    private Search() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd').trim();
    }

    /** True when `q` is empty or any of the fields contains it. */
    public static boolean matches(String q, String... fields) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = normalize(q);
        for (String field : fields) {
            if (normalize(field).contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
