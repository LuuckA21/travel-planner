package me.luucka.travelplanner.itinerary;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.common.InvalidFieldException;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.trip.Trip;
import me.luucka.travelplanner.trip.TripAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityService {

    private final ActivityRepository activities;
    private final TripAccess access;

    ActivityService(ActivityRepository activities, TripAccess access) {
        this.activities = activities;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listForTrip(UUID tripId) {
        return activities.findByTripIdOrderByDateAscPositionAsc(tripId).stream().map(ActivityService::response).toList();
    }

    ActivityResponse create(UUID tripId, UUID userId, ActivityRequest r) {
        Trip trip = access.requireOwned(tripId, userId);
        requireWithinTrip(trip, r);
        return response(activities.save(new Activity(trip, activities.nextPosition(tripId, r.date()), r)));
    }

    ActivityResponse update(UUID id, UUID userId, ActivityRequest r) {
        Activity a = activities.findByIdAndTripOwnerId(id, userId).orElseThrow(NotFoundException::new);
        requireWithinTrip(a.getTrip(), r);
        boolean otherDay = !a.getDate().equals(r.date());
        a.update(r);
        if (otherDay) {
            a.setPosition(activities.nextPosition(a.getTrip().getId(), r.date()));
        }
        return response(a);
    }

    void delete(UUID id, UUID userId) {
        activities.delete(activities.findByIdAndTripOwnerId(id, userId).orElseThrow(NotFoundException::new));
    }

    void reorder(UUID tripId, UUID userId, ReorderRequest r) {
        access.requireOwned(tripId, userId);
        List<Activity> day = activities.findByTripIdAndDate(tripId, r.date());
        List<UUID> ids = r.activityIds();
        if (ids.size() != day.size() || !new HashSet<>(ids).equals(new HashSet<>(day.stream().map(Activity::getId).toList()))) {
            throw new InvalidFieldException("activityIds", "Must list every activity of the day exactly once");
        }
        for (Activity a : day) {
            a.setPosition(ids.indexOf(a.getId()));
        }
    }

    private static void requireWithinTrip(Trip trip, ActivityRequest r) {
        if (r.date().isBefore(trip.getStartDate()) || r.date().isAfter(trip.getEndDate())) {
            throw new InvalidFieldException("date", "Date must be within the trip dates");
        }
    }

    private static ActivityResponse response(Activity a) {
        return new ActivityResponse(a.getId(), a.getDate(), a.getPosition(), a.getTitle(), a.getStartTime(),
                a.getLocation(), a.getMapsUrl(), MapsLinks.of(a.getLocation(), a.getMapsUrl()), a.getNotes());
    }
}
