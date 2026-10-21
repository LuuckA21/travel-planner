package me.luucka.travelplanner.trip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.booking.BookingService;
import me.luucka.travelplanner.checklist.ChecklistService;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.common.storage.StoredFiles;
import me.luucka.travelplanner.document.DocumentService;
import me.luucka.travelplanner.itinerary.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
class TripService {

    private final TripRepository trips;
    private final TripAccess access;
    private final ActivityService activities;
    private final BookingService bookings;
    private final DocumentService documents;
    private final ChecklistService checklists;
    private final StoredFiles files;

    record CoverFile(Path path, String contentType) {}

    TripService(TripRepository trips, TripAccess access, ActivityService activities,
                BookingService bookings, DocumentService documents, ChecklistService checklists,
                StoredFiles files) {
        this.trips = trips;
        this.access = access;
        this.activities = activities;
        this.bookings = bookings;
        this.documents = documents;
        this.checklists = checklists;
        this.files = files;
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
        checklists.createDefaults(trip);
        return detail(trip);
    }

    TripDetail update(UUID tripId, UUID userId, TripRequest r) {
        Trip trip = access.requireOwned(tripId, userId);
        trip.update(r.name(), r.destinations(), r.startDate(), r.endDate());
        return detail(trip);
    }

    void delete(UUID tripId, UUID userId) {
        Trip trip = access.requireOwned(tripId, userId);
        List<String> keys = new ArrayList<>(documents.storageKeysForTrip(tripId));
        if (trip.getCoverStorageKey() != null) {
            keys.add(trip.getCoverStorageKey());
        }
        trips.delete(trip);
        files.deleteAfterCommit(keys);
        trips.flush();
    }

    TripSummary setCover(UUID tripId, UUID userId, MultipartFile file) {
        Trip trip = access.requireOwned(tripId, userId);
        String old = trip.getCoverStorageKey();
        return files.store(file, true, s -> {
            trip.setCoverStorageKey(s.key());
            if (old != null) {
                files.deleteAfterCommit(List.of(old));
            }
            trips.flush();
            return summary(trip);
        });
    }

    void deleteCover(UUID tripId, UUID userId) {
        Trip trip = access.requireOwned(tripId, userId);
        if (trip.getCoverStorageKey() != null) {
            files.deleteAfterCommit(List.of(trip.getCoverStorageKey()));
            trip.setCoverStorageKey(null);
            trips.flush();
        }
    }

    @Transactional(readOnly = true)
    CoverFile cover(UUID tripId, UUID userId) {
        String key = access.requireOwned(tripId, userId).getCoverStorageKey();
        if (key == null) {
            throw new NotFoundException();
        }
        Path path = files.require(key);
        return new CoverFile(path, files.typeOf(path));
    }

    private static String coverUrl(Trip t) {
        return t.getCoverStorageKey() == null ? null : "/api/trips/" + t.getId() + "/cover?v=" + t.getCoverStorageKey();
    }

    private static TripSummary summary(Trip t) {
        return new TripSummary(t.getId(), t.getName(), List.copyOf(t.getDestinations()), t.getStartDate(),
                t.getEndDate(), coverUrl(t));
    }

    private TripDetail detail(Trip t) {
        return new TripDetail(t.getId(), t.getName(), List.copyOf(t.getDestinations()), t.getStartDate(),
                t.getEndDate(), coverUrl(t), activities.listForTrip(t.getId()),
                bookings.listForTrip(t.getId()), documents.listForTrip(t.getId()), checklists.listForTrip(t.getId()));
    }
}
