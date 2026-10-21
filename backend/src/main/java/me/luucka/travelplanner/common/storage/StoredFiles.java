package me.luucka.travelplanner.common.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import me.luucka.travelplanner.common.ApiException;
import me.luucka.travelplanner.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Upload (size check, type sniff, store, cleanup), deferred deletion and file responses shared by documents and covers. */
@Component
public class StoredFiles {

    private static final Logger log = LoggerFactory.getLogger(StoredFiles.class);
    static final long MAX_BYTES = 25L * 1024 * 1024;

    public record Stored(String key, String type) {}

    private final FileStorage storage;

    StoredFiles(FileStorage storage) {
        this.storage = storage;
    }

    /** Validates and stores the upload, then runs persist; the stored file is removed if anything fails. */
    public <T> T store(MultipartFile file, boolean imageOnly, Function<Stored, T> persist) {
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds 25 MB");
        }
        String key = null;
        try (var in = new BufferedInputStream(file.getInputStream())) {
            in.mark(12);
            byte[] head = in.readNBytes(12);
            in.reset();
            String type = FileTypeDetector.detect(head).filter(t -> !imageOnly || t.startsWith("image/"))
                    .orElseThrow(UnsupportedFileTypeException::new);
            key = storage.store(in);
            return persist.apply(new Stored(key, type));
        } catch (IOException | RuntimeException e) {
            if (key != null) {
                deleteNow(key);
            }
            throw e instanceof IOException io ? new UncheckedIOException(io) : (RuntimeException) e;
        }
    }

    /** Removes the files once the surrounding transaction commits; a rollback keeps them. */
    public void deleteAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(this::deleteNow);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(StoredFiles.this::deleteNow);
            }
        });
    }

    /** Path of the stored file; 404 when the file is gone from disk. */
    public Path require(String key) {
        Path path = storage.path(key);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException();
        }
        return path;
    }

    /** Content type detected from the stored bytes. */
    public String typeOf(Path path) {
        try (var in = Files.newInputStream(path)) {
            return FileTypeDetector.detect(in.readNBytes(12)).orElseThrow(NotFoundException::new);
        } catch (IOException e) {
            throw new NotFoundException();
        }
    }

    public static ResponseEntity<Resource> response(Path path, String contentType, String filename,
                                                    boolean download) {
        var disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(new FileSystemResource(path));
    }

    private void deleteNow(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", key, e);
        }
    }
}
