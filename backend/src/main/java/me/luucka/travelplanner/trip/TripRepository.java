package me.luucka.travelplanner.trip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Trip> findByOwnerIdOrderByStartDateAsc(UUID ownerId);
}
