package vn.racehorse.horse.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;

/** Where horse photo files live, outside the database (TECH-179); same idea as the video storage. */
public interface PhotoStorage {

    /** Stores the bytes under a new random key ending in the given extension (jpg or png) and returns the key. */
    String store(byte[] content, String extension) throws IOException;

    Resource open(String key);

    /** Removes the file; a missing file is not an error. */
    void delete(String key);
}
