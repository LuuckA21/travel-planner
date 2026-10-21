package me.luucka.travelplanner.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoredFilesTest {

    @TempDir
    Path root;

    @Test
    void oneFailingDeleteDoesNotStopTheOthers() throws Exception {
        FileStorage storage = new FileStorage(root.toString());
        String key = storage.store(new ByteArrayInputStream(new byte[] {1}));

        // "not-a-uuid" makes FileStorage throw IllegalArgumentException.
        new StoredFiles(storage).deleteAfterCommit(List.of("not-a-uuid", key));

        assertThat(Files.exists(storage.path(key))).isFalse();
    }
}
