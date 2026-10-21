package me.luucka.travelplanner.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.storage-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stores the stream under a new random key. */
    public String store(InputStream in) throws IOException {
        String key = UUID.randomUUID().toString();
        Path tmp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, root.resolve(key), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return key;
    }

    public Path path(String key) {
        // Round-trip through UUID so only canonical lowercase UUIDs reach the filesystem.
        if (!UUID.fromString(key).toString().equals(key)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return root.resolve(key);
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }
}
