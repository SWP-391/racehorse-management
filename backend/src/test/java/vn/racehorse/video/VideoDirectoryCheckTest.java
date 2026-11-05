package vn.racehorse.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import vn.racehorse.video.storage.FileSystemVideoStorage;

/** TECH-111: the video directory check behind the warning shown when a mounted volume is not writable. */
class VideoDirectoryCheckTest {

    @Test
    void aWritableOrCreatableDirectoryIsUsable(@TempDir Path tmp) {
        assertThat(FileSystemVideoStorage.isUsable(tmp)).isTrue();
        assertThat(FileSystemVideoStorage.isUsable(tmp.resolve("new/sub"))).isTrue();
    }

    @Test
    void aDirectoryUnderAFileIsNotUsable(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("a-file"), "x");
        assertThat(FileSystemVideoStorage.isUsable(file.resolve("videos"))).isFalse();
    }
}
