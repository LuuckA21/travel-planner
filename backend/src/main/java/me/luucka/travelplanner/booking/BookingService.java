package me.luucka.travelplanner.booking;

import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.common.InvalidFieldException;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.trip.TripAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookings;
    private final TripAccess access;

    BookingService(BookingRepository bookings, TripAccess access) {
        this.bookings = bookings;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForTrip(UUID tripId) {
        return bookings.findByTripIdOrderByStartAtAsc(tripId).stream().map(Booking::toResponse).toList();
    }

    /** Ownership-checked lookup, shared with the document feature. */
    public Booking requireOwned(UUID bookingId, UUID userId) {
        return bookings.findByIdAndTripOwnerId(bookingId, userId).orElseThrow(NotFoundException::new);
    }

    BookingResponse create(UUID tripId, UUID userId, BookingRequest r) {
        var trip = access.requireOwned(tripId, userId);
        requireEndAfterStart(r);
        return bookings.save(new Booking(trip, r)).toResponse();
    }

    BookingResponse update(UUID id, UUID userId, BookingRequest r) {
        Booking b = requireOwned(id, userId);
        requireEndAfterStart(r);
        b.update(r);
        return b.toResponse();
    }

    void delete(UUID id, UUID userId) {
        bookings.delete(requireOwned(id, userId));
    }

    private static void requireEndAfterStart(BookingRequest r) {
        if (r.endAt() != null && r.endAt().isBefore(r.startAt())) {
            throw new InvalidFieldException("endAt", "End must not be before start");
        }
    }
}
