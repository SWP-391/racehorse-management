package vn.racehorse.video.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Video files in one directory, named by a random key and never by the client's file name (TECH-052). */
@Component
public class FileSystemVideoStorage implements VideoStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");

    private final Path root;

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemVideoStorage.class);

    public FileSystemVideoStorage(@Value("${app.video.dir:./data/videos}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        if (!isUsable(root)) {
            // Not fatal: the rest of the system works; only video upload fails (TECH-111).
            LOG.warn("Video directory {} cannot be created or written; video upload will fail. "
                    + "On a hosting volume owned by root, run the service with permission to write there "
                    + "(Railway: set RAILWAY_RUN_UID=0).", root);
        }
    }

    /** True when the directory exists or can be created, and can be written. */
    public static boolean isUsable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public Stored store(InputStream content) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID().toString().replace("-", "");
        Path target = resolve(key);
        try {
            long size = Files.copy(content, target);
            return new Stored(key, size);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

    @Override
    public Resource open(String key) {
        return new FileSystemResource(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid video key");
        }
        return root.resolve(key + ".mp4");
    }
}
