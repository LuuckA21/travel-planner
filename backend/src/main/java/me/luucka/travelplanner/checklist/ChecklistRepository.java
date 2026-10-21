package me.luucka.travelplanner.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ChecklistRepository extends JpaRepository<Checklist, UUID> {

    Optional<Checklist> findByIdAndTripOwnerId(UUID id, UUID ownerId);

    List<Checklist> findByTripIdOrderByPositionAsc(UUID tripId);

    /** Max + 1 rather than count: deletes leave gaps. */
    @Query("select coalesce(max(c.position), -1) + 1 from Checklist c where c.trip.id = :tripId")
    int nextPosition(UUID tripId);
}
