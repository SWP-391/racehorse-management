package vn.racehorse.horse.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Photos are kept under random keys, never under the client's file name (TECH-179). */
class FileSystemPhotoStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesUnderARandomKeyAndDeletes() throws Exception {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(dir.toString());
        String key = storage.store(new byte[] {1, 2, 3}, "png");
        assertThat(key).matches("[0-9a-f]{32}\\.png");
        assertThat(Files.readAllBytes(dir.resolve(key))).containsExactly(1, 2, 3);
        assertThat(storage.store(new byte[] {1}, "png")).isNotEqualTo(key);
        storage.delete(key);
        assertThat(Files.exists(dir.resolve(key))).isFalse();
        storage.delete(key); // a missing file is not an error
    }

    @Test
    void aKeyThatIsNotOurOwnIsRefusedSoNoPathCanEscape() {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(dir.toString());
        assertThatThrownBy(() -> storage.open("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("x.png")).isInstanceOf(IllegalArgumentException.class);
    }
}
