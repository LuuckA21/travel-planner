package me.luucka.travelplanner.document;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.booking.BookingService;
import me.luucka.travelplanner.common.ApiException;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.common.storage.FileStorage;
import me.luucka.travelplanner.common.storage.FileTypeDetector;
import me.luucka.travelplanner.common.storage.UnsupportedFileTypeException;
import me.luucka.travelplanner.trip.TripAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    static final long MAX_BYTES = 25L * 1024 * 1024;

    record FileContent(Path path, String contentType, String filename) {}

    private final TripDocumentRepository documents;
    private final TripAccess access;
    private final BookingService bookings;
    private final FileStorage storage;

    DocumentService(TripDocumentRepository documents, TripAccess access, BookingService bookings,
                    FileStorage storage) {
        this.documents = documents;
        this.access = access;
        this.bookings = bookings;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForTrip(UUID tripId) {
        return documents.findByTripIdOrderByCreatedAtAsc(tripId).stream().map(TripDocument::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> storageKeysForTrip(UUID tripId) {
        return documents.findStorageKeysByTripId(tripId);
    }

    /** Best-effort removal of stored files; call after the rows are gone. */
    public void deleteFiles(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (IOException e) {
                log.warn("Could not delete stored file {}", key, e);
            }
        }
    }

    DocumentResponse upload(UUID tripId, UUID userId, MultipartFile file, UUID bookingId) {
        var trip = access.requireOwned(tripId, userId);
        if (bookingId != null && !bookings.requireOwned(bookingId, userId).getTrip().getId().equals(tripId)) {
            throw new NotFoundException();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds 25 MB");
        }
        String key = null;
        try (var in = new BufferedInputStream(file.getInputStream())) {
            in.mark(12);
            byte[] head = in.readNBytes(12);
            in.reset();
            String type = FileTypeDetector.detect(head).orElseThrow(UnsupportedFileTypeException::new);
            key = storage.store(in);
            var doc = documents.save(new TripDocument(trip, bookingId, filenameOf(file), type, file.getSize(), key));
            documents.flush();
            return doc.toResponse();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            if (key != null) {
                deleteFiles(List.of(key));
            }
            throw e;
        }
    }

    FileContent open(UUID id, UUID userId) {
        TripDocument d = require(id, userId);
        return new FileContent(storage.path(d.getStorageKey()), d.getContentType(), d.getOriginalFilename());
    }

    void delete(UUID id, UUID userId) {
        TripDocument d = require(id, userId);
        documents.delete(d);
        documents.flush();
        deleteFiles(List.of(d.getStorageKey()));
    }

    private TripDocument require(UUID id, UUID userId) {
        return documents.findByIdAndTripOwnerId(id, userId).orElseThrow(NotFoundException::new);
    }

    private static String filenameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        name = name == null ? "" : name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        return name.isEmpty() ? "document" : name.substring(0, Math.min(name.length(), 255));
    }
}
