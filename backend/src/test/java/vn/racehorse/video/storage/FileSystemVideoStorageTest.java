package vn.racehorse.video.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import vn.racehorse.video.TrialVideosTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TECH-052: random keys in one directory; a key cannot point outside it. */
class FileSystemVideoStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesOpensAndDeletes() throws Exception {
        FileSystemVideoStorage storage = new FileSystemVideoStorage(dir.toString());
        byte[] content = TrialVideosTest.mp4(100);

        VideoStorage.Stored stored = storage.store(new ByteArrayInputStream(content));

        assertThat(stored.size()).isEqualTo(100);
        assertThat(stored.key()).matches("[0-9a-f]{32}");
        Resource resource = storage.open(stored.key());
        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly(stored.key() + ".mp4");
        }

        storage.delete(stored.key());
        assertThat(storage.open(stored.key()).exists()).isFalse();
        storage.delete(stored.key());
    }

    @Test
    void refusesKeysThatCouldLeaveTheDirectory() {
        FileSystemVideoStorage storage = new FileSystemVideoStorage(dir.toString());
        assertThatThrownBy(() -> storage.open("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("..")).isInstanceOf(IllegalArgumentException.class);
    }
}
