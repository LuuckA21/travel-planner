package me.luucka.travelplanner.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import me.luucka.travelplanner.trip.Trip;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Enumerated(EnumType.STRING)
    private BookingType type;

    private String title;
    private String provider;
    private String reference;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String origin;
    private String location;
    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Booking() {}

    Booking(Trip trip, BookingRequest r) {
        this.trip = trip;
        update(r);
    }

    public UUID getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    void update(BookingRequest r) {
        this.type = r.type();
        this.title = r.title();
        this.provider = r.provider();
        this.reference = r.reference();
        this.startAt = r.startAt();
        this.endAt = r.endAt();
        this.origin = r.origin();
        this.location = r.location();
        this.notes = r.notes();
    }

    BookingResponse toResponse() {
        return new BookingResponse(id, type, title, provider, reference, startAt, endAt, origin, location, notes);
    }
}
