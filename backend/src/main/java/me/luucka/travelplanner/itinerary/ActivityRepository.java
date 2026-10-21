package me.luucka.travelplanner.itinerary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findByIdAndTripOwnerId(UUID id, UUID ownerId);

    List<Activity> findByTripIdOrderByDateAscPositionAsc(UUID tripId);

    List<Activity> findByTripIdAndDate(UUID tripId, LocalDate date);

    /** Max + 1 rather than count: deletes and moves leave gaps. */
    @Query("select coalesce(max(a.position), -1) + 1 from Activity a where a.trip.id = :tripId and a.date = :date")
    int nextPosition(UUID tripId, LocalDate date);
}
