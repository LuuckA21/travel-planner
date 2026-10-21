package me.luucka.travelplanner.trip;

import java.util.UUID;
import me.luucka.travelplanner.common.NotFoundException;
import org.springframework.stereotype.Component;

/** Single ownership gate: every feature service loads its trip through here. */
@Component
public class TripAccess {

    private final TripRepository trips;

    TripAccess(TripRepository trips) {
        this.trips = trips;
    }

    public Trip requireOwned(UUID tripId, UUID userId) {
        return trips.findByIdAndOwnerId(tripId, userId).orElseThrow(NotFoundException::new);
    }
}
