package me.luucka.travelplanner.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndTripOwnerId(UUID id, UUID ownerId);

    List<Booking> findByTripIdOrderByStartAtAsc(UUID tripId);
}
