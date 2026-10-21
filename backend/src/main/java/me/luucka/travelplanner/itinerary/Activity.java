package me.luucka.travelplanner.itinerary;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import me.luucka.travelplanner.trip.Trip;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class Activity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    private LocalDate date;
    private int position;
    private String title;
    private LocalTime startTime;
    private String location;
    private String mapsUrl;
    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Activity() {}

    Activity(Trip trip, int position, ActivityRequest r) {
        this.trip = trip;
        this.position = position;
        update(r);
    }

    void update(ActivityRequest r) {
        this.date = r.date();
        this.title = r.title();
        this.startTime = r.startTime();
        this.location = r.location();
        this.mapsUrl = r.mapsUrl();
        this.notes = r.notes();
    }

    Trip getTrip() {
        return trip;
    }

    UUID getId() {
        return id;
    }

    LocalDate getDate() {
        return date;
    }

    int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    String getTitle() {
        return title;
    }

    LocalTime getStartTime() {
        return startTime;
    }

    String getLocation() {
        return location;
    }

    String getMapsUrl() {
        return mapsUrl;
    }

    String getNotes() {
        return notes;
    }
}
