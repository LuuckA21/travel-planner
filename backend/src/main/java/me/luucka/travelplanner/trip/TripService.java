package me.luucka.travelplanner.trip;

import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.itinerary.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TripService {

    private final TripRepository trips;
    private final TripAccess access;
    private final ActivityService activities;

    TripService(TripRepository trips, TripAccess access, ActivityService activities) {
        this.trips = trips;
        this.access = access;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    List<TripSummary> list(UUID userId) {
        return trips.findByOwnerIdOrderByStartDateAsc(userId).stream().map(TripService::summary).toList();
    }

    @Transactional(readOnly = true)
    TripDetail get(UUID tripId, UUID userId) {
        return detail(access.requireOwned(tripId, userId));
    }

    TripDetail create(UUID userId, TripRequest r) {
        Trip trip = trips.save(new Trip(userId, r.name(), r.destinations(), r.startDate(), r.endDate()));
        return detail(trip);
    }

    TripDetail update(UUID tripId, UUID userId, TripRequest r) {
        Trip trip = access.requireOwned(tripId, userId);
        trip.update(r.name(), r.destinations(), r.startDate(), r.endDate());
        return detail(trip);
    }

    void delete(UUID tripId, UUID userId) {
        trips.delete(access.requireOwned(tripId, userId));
    }

    private static TripSummary summary(Trip t) {
        return new TripSummary(t.getId(), t.getName(), List.copyOf(t.getDestinations()), t.getStartDate(),
                t.getEndDate(), null);
    }

    private TripDetail detail(Trip t) {
        return new TripDetail(t.getId(), t.getName(), List.copyOf(t.getDestinations()), t.getStartDate(),
                t.getEndDate(), null, activities.listForTrip(t.getId()));
    }
}
