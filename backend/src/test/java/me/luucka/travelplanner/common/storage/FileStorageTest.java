package me.luucka.travelplanner.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageTest {

    @TempDir
    Path root;

    @Test
    void storeAndDelete() throws Exception {
        FileStorage storage = new FileStorage(root.resolve("files").toString());

        String key = storage.store(new ByteArrayInputStream("hello".getBytes()));

        assertThat(storage.path(key)).exists().hasContent("hello");
        assertThat(storage.path(key).getParent()).isEqualTo(root.resolve("files").toAbsolutePath().normalize());
        try (var entries = Files.list(storage.path(key).getParent())) {
            assertThat(entries).hasSize(1); // no temp file left behind
        }

        storage.delete(key);
        assertThat(storage.path(key)).doesNotExist();
        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();
    }

    @Test
    void pathRejectsNonUuidKey() {
        FileStorage storage = new FileStorage(root.toString());

        assertThatThrownBy(() -> storage.path("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
