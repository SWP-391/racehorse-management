package vn.racehorse.video.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where video files live, outside the database (TECH-005, TECH-052). The PoC writes to a directory; a cloud object
 * store can replace it without touching the business rules.
 */
public interface VideoStorage {

    /** Stores the stream under a new key and returns the key with the number of bytes written. */
    Stored store(InputStream content) throws IOException;

    /** The stored file, readable more than once so playback can serve byte ranges. */
    Resource open(String key);

    /** Removes the file; a missing file is not an error. */
    void delete(String key);

    record Stored(String key, long size) {
    }
}
