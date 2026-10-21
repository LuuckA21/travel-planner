package me.luucka.travelplanner.document;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.booking.BookingService;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.common.storage.StoredFiles;
import me.luucka.travelplanner.trip.TripAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DocumentService {

    record FileContent(Path path, String contentType, String filename) {}

    private final TripDocumentRepository documents;
    private final TripAccess access;
    private final BookingService bookings;
    private final StoredFiles files;

    DocumentService(TripDocumentRepository documents, TripAccess access, BookingService bookings,
                    StoredFiles files) {
        this.documents = documents;
        this.access = access;
        this.bookings = bookings;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForTrip(UUID tripId) {
        return documents.findByTripIdOrderByCreatedAtAsc(tripId).stream().map(TripDocument::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<String> storageKeysForTrip(UUID tripId) {
        return documents.findStorageKeysByTripId(tripId);
    }

    DocumentResponse upload(UUID tripId, UUID userId, MultipartFile file, UUID bookingId) {
        var trip = access.requireOwned(tripId, userId);
        if (bookingId != null && !bookings.requireOwned(bookingId, userId).getTrip().getId().equals(tripId)) {
            throw new NotFoundException();
        }
        return files.store(file, false, s -> {
            var doc = documents.save(new TripDocument(trip, bookingId, filenameOf(file), s.type(), file.getSize(),
                    s.key()));
            documents.flush();
            return doc.toResponse();
        });
    }

    FileContent open(UUID id, UUID userId) {
        TripDocument d = require(id, userId);
        return new FileContent(files.require(d.getStorageKey()), d.getContentType(), d.getOriginalFilename());
    }

    void delete(UUID id, UUID userId) {
        TripDocument d = require(id, userId);
        documents.delete(d);
        files.deleteAfterCommit(List.of(d.getStorageKey()));
        documents.flush();
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
