package me.luucka.travelplanner.document;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import me.luucka.travelplanner.trip.Trip;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class TripDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    private UUID bookingId;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String storageKey;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected TripDocument() {}

    TripDocument(Trip trip, UUID bookingId, String originalFilename, String contentType, long sizeBytes,
                 String storageKey) {
        this.trip = trip;
        this.bookingId = bookingId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    String getContentType() {
        return contentType;
    }

    String getStorageKey() {
        return storageKey;
    }

    DocumentResponse toResponse() {
        return new DocumentResponse(id, bookingId, originalFilename, contentType, sizeBytes, createdAt,
                "/api/documents/" + id + "/file");
    }
}
