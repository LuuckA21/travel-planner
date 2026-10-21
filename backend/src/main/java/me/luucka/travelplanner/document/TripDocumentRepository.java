package me.luucka.travelplanner.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TripDocumentRepository extends JpaRepository<TripDocument, UUID> {

    Optional<TripDocument> findByIdAndTripOwnerId(UUID id, UUID ownerId);

    List<TripDocument> findByTripIdOrderByCreatedAtAsc(UUID tripId);

    // Keys only: loading entities would leave them in the context referencing the trip about to be deleted.
    @Query("select d.storageKey from TripDocument d where d.trip.id = :tripId")
    List<String> findStorageKeysByTripId(UUID tripId);
}
