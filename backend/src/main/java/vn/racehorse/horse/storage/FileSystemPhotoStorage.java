package vn.racehorse.horse.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Photo files in one directory, named by a random key and never by the client's file name (TECH-179). */
@Component
public class FileSystemPhotoStorage implements PhotoStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");
    private static final Logger LOG = LoggerFactory.getLogger(FileSystemPhotoStorage.class);

    private final Path root;

    public FileSystemPhotoStorage(@Value("${app.photo.dir:./data/photos}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        if (!isUsable(root)) {
            // Not fatal: only horse photo upload fails (TECH-179).
            LOG.warn("Photo directory {} cannot be created or written; horse photo upload will fail. "
                    + "On a hosting volume owned by root, run the service with permission to write there "
                    + "(Railway: set RAILWAY_RUN_UID=0).", root);
        }
    }

    static boolean isUsable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public String store(byte[] content, String extension) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolve(key);
        try {
            Files.write(target, content);
            return key;
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
            throw new IllegalArgumentException("Invalid photo key");
        }
        return root.resolve(key);
    }
}
