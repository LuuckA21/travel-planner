package me.luucka.travelplanner.trip;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class Trip {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID ownerId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverStorageKey;

    @ElementCollection
    @CollectionTable(name = "trip_destination", joinColumns = @JoinColumn(name = "trip_id"))
    @OrderColumn(name = "position")
    @Column(name = "name")
    private List<String> destinations = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected Trip() {}

    public Trip(UUID ownerId, String name, List<String> destinations, LocalDate startDate, LocalDate endDate) {
        this.ownerId = ownerId;
        update(name, destinations, startDate, endDate);
    }

    public void update(String name, List<String> destinations, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.destinations.clear();
        this.destinations.addAll(destinations);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public List<String> getDestinations() {
        return destinations;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCoverStorageKey() {
        return coverStorageKey;
    }

    public void setCoverStorageKey(String coverStorageKey) {
        this.coverStorageKey = coverStorageKey;
    }
}
